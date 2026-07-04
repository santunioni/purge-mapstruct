plugins {
    id("org.openrewrite.build.recipe-library-base") version "latest.release"
    id("org.openrewrite.build.publish") version "latest.release"
    id("org.openrewrite.build.recipe-repositories") version "latest.release"
    id("nebula.release") version "21.0.0"
    kotlin("jvm") version "1.9.24"
    id("com.diffplug.spotless") version "8.4.0"
}

// Version is managed by nebula.release:
//   snapshot build → ./gradlew snapshot publish
//   release build → ./gradlew final publish closeAndReleaseSonatypeStagingRepository
group = "io.github.santunioni"
description = "Purge Mapstruct"

// Apply the Nexus publish plugin (already on the classpath via rewrite-build-gradle-plugin)
// to get the `closeAndReleaseSonatypeStagingRepository` task and proper staging workflow.
apply(plugin = "io.github.gradle-nexus.publish-plugin")

recipeDependencies {
    parserClasspath("org.jspecify:jspecify:1.0.0")
}

dependencies {

    // The bom version can also be set to a specific version
    // https://github.com/openrewrite/rewrite-recipe-bom/releases
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))

    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies")
    implementation("org.openrewrite:rewrite-yaml")
    implementation("org.openrewrite:rewrite-xml")
    implementation("org.openrewrite.meta:rewrite-analysis")
    implementation("org.assertj:assertj-core:latest.release")

    // Refaster style recipes need the rewrite-templating annotation processor and dependency for generated recipes
    // https://github.com/openrewrite/rewrite-templating/releases
    annotationProcessor("org.openrewrite:rewrite-templating:latest.release")
    implementation("org.openrewrite:rewrite-templating")
    // The `@BeforeTemplate` and `@AfterTemplate` annotations are needed for refaster style recipes
    compileOnly("com.google.errorprone:error_prone_core:latest.release") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop", "dataflow-errorprone")
    }

    // For IntelliJ Plugin to work
    runtimeOnly("org.openrewrite.recipe:rewrite-rewrite")

    // The RewriteTest class needed for testing recipes
    testImplementation("org.openrewrite:rewrite-test") {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }

    // Support for parsing different Java versions
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly("org.openrewrite:rewrite-java-25")

    // Need to have a slf4j binding to see any output enabled from the parser.
    runtimeOnly("ch.qos.logback:logback-classic:1.5.+")

    // Our recipe converts Guava's `Lists` type
    testRuntimeOnly("com.google.guava:guava:latest.release")
    testRuntimeOnly("org.apache.commons:commons-lang3:latest.release")
    testRuntimeOnly("org.springframework:spring-core:latest.release")
    testRuntimeOnly("org.springframework:spring-context:latest.release")

    // MapStruct for testing PurgeMapstruct recipe and fixtures
    implementation("org.mapstruct:mapstruct:latest.release")
    annotationProcessor("org.mapstruct:mapstruct-processor:latest.release")
    testImplementation("org.mapstruct:mapstruct:latest.release")
    annotationProcessor("org.mapstruct:mapstruct-processor:latest.release")

    // Mockito is exercised by the spy-stubbing rewrite test fixtures.
    testRuntimeOnly("org.mockito:mockito-core:latest.release")
}

signing {
    // Signing is only required for release artifacts; snapshots skip it.
    // CI sets signingKey and signingPassword as Gradle project properties via
    // ORG_GRADLE_PROJECT_signingKey / ORG_GRADLE_PROJECT_signingPassword env vars.
    isRequired = !version.toString().endsWith("SNAPSHOT")
    useInMemoryPgpKeys(
        findProperty("signingKey") as String?,
        findProperty("signingPassword") as String?
    )
}

configure<nebula.plugin.release.git.base.ReleasePluginExtension> {
    defaultVersionStrategy = nebula.plugin.release.NetflixOssStrategies.SNAPSHOT(project)
}

configure<io.github.gradlenexus.publishplugin.NexusPublishExtension> {
    repositories {
        sonatype {
            // Sonatype Central Portal endpoints (the modern OSSRH replacement)
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            // Credentials are read from ORG_GRADLE_PROJECT_sonatypeUsername / sonatypePassword
        }
    }
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")
            pom {
                url.set("https://github.com/santunioni/purge-mapstruct")
                scm {
                    url.set("https://github.com/santunioni/purge-mapstruct")
                    connection.set("scm:git:git://github.com/santunioni/purge-mapstruct.git")
                    developerConnection.set("scm:git:ssh://git@github.com/santunioni/purge-mapstruct.git")
                }
                developers {
                    developer {
                        id.set("santunioni")
                        name.set("Vinícius Vargas")
                        url.set("https://github.com/santunioni")
                    }
                }
            }
        }
    }
}

tasks.register("licenseFormat") {
    println("License format task not implemented for rewrite-recipe-starter")
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        apiVersion = "1.9"
        languageVersion = "1.9"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

// Configure source sets for Kotlin to work alongside Java
sourceSets {
    main {
        java.srcDirs("src/main/java", "src/main/kotlin")
    }
    test {
        java.srcDirs("src/test/java", "src/test/kotlin")
    }
}


spotless {
    java {
        target("src/**/*.java")
        targetExclude("**/build/**")
        googleJavaFormat()
        removeUnusedImports()
        forbidWildcardImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target("src/**/*.kt", "src/**/*.kts")
        targetExclude("**/build/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    groovyGradle {
        target("*.gradle")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(
            "*.md",
            "*.yaml",
            "*.yml",
            "*.properties",
            "src/**/*.md",
            "src/**/*.yaml",
            "src/**/*.yml",
            "src/**/*.properties"
        )
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}