plugins {
    id("org.openrewrite.build.recipe-library-base") version "latest.release"
    id("org.openrewrite.build.publish") version "latest.release"
    id("org.openrewrite.build.recipe-repositories") version "latest.release"
    id("nebula.release") version "21.0.0"
    id("com.diffplug.spotless") version "8.4.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    kotlin("jvm") version "2.4.0"
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

    // The RewriteTest class needed for testing recipes
    testImplementation("org.openrewrite:rewrite-test") {
        exclude(group = "org.slf4j", module = "slf4j-nop")
    }

    // Support for parsing Java source across supported versions
    testRuntimeOnly("org.openrewrite:rewrite-java-17")
    testRuntimeOnly("org.openrewrite:rewrite-java-21")
    testRuntimeOnly("org.openrewrite:rewrite-java-25")

    // Need to have a slf4j binding to see any output enabled from the parser.
    runtimeOnly("ch.qos.logback:logback-classic:1.5.+")

    // MapStruct needed on parser classpath for test fixtures
    testRuntimeOnly("org.mapstruct:mapstruct:latest.release")

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
        findProperty("signingPassword") as String?,
    )
}

configure<nebula.plugin.release.git.base.ReleasePluginExtension> {
    defaultVersionStrategy =
        nebula.plugin.release.NetflixOssStrategies
            .SNAPSHOT(project)
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

kotlin {
    jvmToolchain(17)
    sourceSets {
        main {
            kotlin.srcDirs("src/main/java", "src/main/kotlin")
        }
        test {
            kotlin.srcDirs("src/test/java", "src/test/kotlin")
        }
    }
}

tasks.withType<JavaCompile> {
    options.release.set(17)
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// Re-run the test suite under each supported Java version.
// JavaParser.fromJavaVersion() picks the right parser automatically based on the JVM.
listOf(21, 25).forEach { version ->
    val taskName = "testJava$version"
    tasks.register<Test>(taskName) {
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(version))
            },
        )
    }
    tasks.named("check") {
        dependsOn(taskName)
    }
}

spotless {
    kotlin {
        ktlint()
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
            "src/**/*.properties",
        )
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    config.setFrom(rootDir.resolve("detekt.yml"))
    buildUponDefaultConfig = true
}
