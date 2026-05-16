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
// OpenAPI generator: regenerates the REST client from spec/server-v1.json
// into build/generated/openapi. The main source set picks up the generated
// kotlin so callers get typed `UsersApi` + `SessionsApi`.
// ---------------------------------------------------------------------------

val openApiOutput = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/spec/server-v1.json")
    outputDir.set(openApiOutput.get().asFile.absolutePath)
    apiPackage.set("so.torii.backend.generated.api")
    modelPackage.set("so.torii.backend.generated.model")
    invokerPackage.set("so.torii.backend.generated.infrastructure")
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

sourceSets {
    main {
        kotlin.srcDir(openApiOutput.map { it.dir("src/main/kotlin") })
    }
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("openApiGenerate"))
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

    // Spring Security adapter — compileOnly so consumers without Spring
    // don't pull these onto their runtime classpath.
    compileOnly("org.springframework.security:spring-security-core:6.3.3")
    compileOnly("org.springframework.security:spring-security-web:6.3.3")
    compileOnly("org.springframework:spring-web:6.1.13")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.security:spring-security-core:6.3.3")
    testImplementation("org.springframework.security:spring-security-web:6.3.3")
    testImplementation("org.springframework:spring-web:6.1.13")
    testImplementation("org.springframework:spring-test:6.1.13")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
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
                    url.set("https://github.com/GOOD-Code-ApS/torii")
                }
            }
        }
    }
}
