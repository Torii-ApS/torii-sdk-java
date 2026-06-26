import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.JavaLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.openapi.generator") version "7.23.0"
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "so.torii"
version = "0.0.6"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    // sources jar + javadoc jar are configured by `mavenPublishing { configure(JavaLibrary(...)) }` below.
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Generated OpenAPI code can produce warnings; don't fail the build on them.
        allWarningsAsErrors.set(false)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// ---------------------------------------------------------------------------
// OpenAPI generator: produces the REST client from spec/server-v1.json into
// src/main/kotlin/so/torii/backend/generated/. The generated sources are
// **committed** so the layout matches the other torii SDKs (the `generated/`
// directory is browsable on GitHub, no Gradle step required to read the
// client). Regenerate after a spec update with:
//
//   ./gradlew regenerateOpenApi
//
// The generator first emits into a staging dir under build/ (so the cruft
// — README, build.gradle, docs — stays out of the repo), then the Sync task
// copies only the Kotlin sources into the committed location.
// ---------------------------------------------------------------------------

val openApiStaging = layout.buildDirectory.dir("openapi-staging")
val generatedSourcesDir = file("$projectDir/src/main/kotlin/so/torii/backend/generated")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/spec/server-v1.json")
    outputDir.set(openApiStaging.get().asFile.absolutePath)
    apiPackage.set("so.torii.backend.generated.api")
    modelPackage.set("so.torii.backend.generated.model")
    // `packageName` controls the *infrastructure* (invoker) package on the
    // Kotlin generator. The previous `invokerPackage` setting was a no-op
    // here — it's a Java-target property. Without packageName, infrastructure
    // would land at the default `org.openapitools.client.infrastructure`.
    packageName.set("so.torii.backend.generated")
    // jvm-okhttp4 is the most actively maintained Kotlin client target.
    // kotlinx_serialization keeps dependencies aligned with the hand-written code.
    configOptions.set(
        mapOf(
            "library" to "jvm-okhttp4",
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "java8",
            "useCoroutines" to "true",
            "enumPropertyNaming" to "UPPERCASE",
            "sourceFolder" to "src/main/kotlin",
            "omitGradleWrapper" to "true",
        ),
    )
}

// Single command for contributors after a spec bump. Replaces the previous
// `openApiGenerate` workflow that emitted into build/ and was wired into
// compileKotlin as a build-time step.
tasks.register<Sync>("regenerateOpenApi") {
    group = "openapi"
    description = "Regenerates the REST client from spec/server-v1.json and copies it into src/main/kotlin/so/torii/backend/generated/."
    dependsOn("openApiGenerate")
    from(openApiStaging.map { it.dir("src/main/kotlin/so/torii/backend/generated") })
    into(generatedSourcesDir)
    doLast {
        // kotlinx.serialization can't synthesise a serializer for `kotlin.Any`,
        // so the kotlin generator's `@Contextual Map<String, kotlin.Any>` for
        // every free-form object property (additionalProperties: true — e.g.
        // ProblemDetail.properties and the user metadata bags) crashes the
        // serialization compiler backend at compile time. Rewrite the value
        // type to JsonElement (which has a first-class serializer) across every
        // generated model. Idempotent: a no-op once already rewritten.
        val modelDir = file("$generatedSourcesDir/model")
        modelDir.listFiles { f -> f.isFile && f.extension == "kt" }?.forEach { f ->
            val text = f.readText()
            val patched = text.replace(
                "kotlin.collections.Map<kotlin.String, kotlin.Any>",
                "kotlin.collections.Map<kotlin.String, kotlinx.serialization.json.JsonElement>",
            )
            if (patched != text) f.writeText(patched)
        }

        // Tri-state PATCH/search fields: rewrite every nullable `String` request
        // field to a tri-state `PatchValue<String?>` so callers can distinguish
        // omit / clear / set. Combined with `encodeDefaults = false` (below), a
        // field left at its `PatchValue.NotIncluded` default is dropped from the
        // body (the "leave unchanged" wire state). Scoped to the request models
        // that carry tri-state fields; generic within each (a new nullable-String
        // field is converted automatically, no hand edits). Map/enum/date fields
        // are left untouched (the regex only matches `kotlin.String?`).
        val triStateModels = listOf("UpdateUserRequest.kt", "ServerUserSearchRequest.kt")
        val nullableString = Regex("""val (\w+): kotlin\.String\? = null""")
        triStateModels.forEach { name ->
            val f = file("$generatedSourcesDir/model/$name")
            if (f.exists()) {
                val text = f.readText()
                val patched = nullableString.replace(text) { m ->
                    "val ${m.groupValues[1]}: so.torii.backend.PatchValue<kotlin.String?> = so.torii.backend.PatchValue.NotIncluded"
                }
                if (patched != text) f.writeText(patched)
            }
        }

        // Omit any field left at its default (PatchValue.NotIncluded or null) from
        // request bodies — the omit half of the tri-state contract, and what keeps
        // create/search/metadata bodies free of unset keys.
        val serializerFile = file("$generatedSourcesDir/infrastructure/Serializer.kt")
        if (serializerFile.exists()) {
            val text = serializerFile.readText()
            val patched = text.replace("encodeDefaults = true", "encodeDefaults = false")
            if (patched != text) serializerFile.writeText(patched)
        }
    }
}

dependencies {
    // Kotlin stdlib explicitly so consumers get a clear coordinate.
    implementation(kotlin("stdlib-jdk8"))

    // JWT verification (ES256 + remote JWKS w/ kid rotation + caching).
    api("com.nimbusds:nimbus-jose-jwt:9.40")

    // OkHttp powers the generated REST client.
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // kotlinx-serialization for the generated model classes.
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines — the generated client suspends, and we expose blocking
    // wrappers via runBlocking for Java callers.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

mavenPublishing {
    // Central Portal is the new (post-OSSRH) upload path. automaticRelease = true
    // means a successful upload progresses straight to public; flip to false to
    // hold the staging in the Central Portal UI for manual release.
    publishToMavenCentral(automaticRelease = true)
    // Sign only when a GPG key is configured. Without this guard,
    // `publishToMavenLocal` in environments without signing keys (CI, the
    // contract-test smoke Dockerfile) fails with "no configured signatory"
    // because the publication insists on the .asc artifacts even when the
    // sign tasks are excluded. Central-Portal releases set the standard
    // `signing.*` gradle properties via secrets and stay signed.
    if (project.findProperty("signing.keyId") != null ||
        project.findProperty("signing.gnupg.keyName") != null ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }

    coordinates("so.torii", "torii-backend", version.toString())

    // Auto-creates a maven publication from the java-library component and
    // wires up the sources + javadoc jars (replaces the manual withSourcesJar/
    // withJavadocJar calls).
    configure(
        JavaLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = true,
        ),
    )

    pom {
        name.set("torii backend SDK")
        description.set("Kotlin-first, Java-interoperable backend SDK for torii.")
        url.set("https://torii.so")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("torii")
                name.set("torii")
                email.set("hello@torii.so")
            }
        }
        scm {
            url.set("https://github.com/Torii-ApS/torii-sdk-java")
            connection.set("scm:git:git://github.com/Torii-ApS/torii-sdk-java.git")
            developerConnection.set("scm:git:ssh://git@github.com/Torii-ApS/torii-sdk-java.git")
        }
    }
}
