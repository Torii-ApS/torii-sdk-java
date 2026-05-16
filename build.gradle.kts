import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.openapi.generator") version "7.7.0"
    `java-library`
    `maven-publish`
}

group = "so.torii"
version = "0.0.1"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
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
        // so rewrite the one place the kotlin generator emits it
        // (ProblemDetail.properties: Map<String, Object> in the spec) to use
        // JsonElement instead. Idempotent: a no-op once already rewritten.
        val problemDetail = file("$generatedSourcesDir/model/ProblemDetail.kt")
        if (problemDetail.exists()) {
            val text = problemDetail.readText()
            val patched = text.replace(
                "kotlin.collections.Map<kotlin.String, kotlin.Any>",
                "kotlin.collections.Map<kotlin.String, kotlinx.serialization.json.JsonElement>",
            )
            if (patched != text) problemDetail.writeText(patched)
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "torii-backend"
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
                }
            }
        }
    }
}
