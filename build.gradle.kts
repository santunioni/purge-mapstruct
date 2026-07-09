import org.gradle.plugins.signing.Sign
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig

plugins {
    id("org.openrewrite.build.recipe-library-base") version "latest.release"
    id("org.openrewrite.build.publish") version "latest.release"
    id("org.openrewrite.build.recipe-repositories") version "latest.release"
    id("nebula.release") version "21.0.0"
    id("com.diffplug.spotless") version "8.4.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("io.github.gradle-nexus.publish-plugin") version "latest.release"
    kotlin("jvm") version "2.4.0"
    idea
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.10"
}

// Version is managed by nebula.release:
//   snapshot build → ./gradlew snapshot publish
//   release build → ./gradlew final publish closeAndReleaseSonatypeStagingRepository
group = "io.github.santunioni"
description = "Purge Mapstruct"

kotlin {
    explicitApi() // Can also use explicitApiWarning() to only show warnings
}

recipeDependencies {
    parserClasspath("org.jspecify:jspecify:1.0.0")
}

dependencies {

    // https://github.com/openrewrite/rewrite-recipe-bom/releases
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:3.33.0"))

    implementation("org.openrewrite:rewrite-java")

    // Bundled so that PurgeMapstruct cleanup visitors work out-of-the-box
    // without requiring consumers to add extra rewrite dependencies.
    implementation("org.openrewrite.recipe:rewrite-static-analysis")
    implementation("org.openrewrite.recipe:rewrite-spring")

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

    // Spring annotations (@Component, @Primary, @Autowired, @Qualifier) needed on the parser
    // classpath for the @DecoratedWith decorated-mapper fixtures.
    testRuntimeOnly("org.springframework:spring-context:latest.release")
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
    description = "License Format"
    println("License format task not implemented for rewrite-recipe-starter")
}

tasks.register("deploy") {
    description = "Interactive deployment task: creates version tags and GitHub releases"
    group = "publishing"
    doNotTrackState("Interactive task requires user input")

    doLast {
        val dryRun = project.findProperty("dryRun") == "true" || project.findProperty("dry-run") == "true"
        val isRC = project.findProperty("rc") == "true"

        val colors =
            mapOf(
                "blue" to "\u001B[34m",
                "green" to "\u001B[32m",
                "yellow" to "\u001B[33m",
                "red" to "\u001B[31m",
                "dim" to "\u001B[2m",
                "reset" to "\u001B[0m",
            )

        fun print(
            msg: String,
            color: String = "reset",
        ) = println("${colors[color]}$msg${colors["reset"]}")

        fun printHeader(msg: String) {
            println()
            print("╔════════════════════════════════════════╗", "blue")
            print("║  $msg", "blue")
            print("╚════════════════════════════════════════╝", "blue")
            println()
        }

        fun exec(vararg cmd: String): String {
            val process =
                ProcessBuilder(*cmd)
                    .redirectErrorStream(true)
                    .start()
            return process.inputStream
                .bufferedReader()
                .use { it.readText() }
                .trim()
        }

        fun prompt(msg: String): String? {
            System.out.print("${colors["yellow"]}$msg${colors["reset"]}")
            System.out.flush()
            return System.`in`.bufferedReader().readLine()
        }

        fun parseVersion(version: String): Triple<Int, Int, Int> {
            val parts = version.split(Regex("[.-]"))
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return Triple(major, minor, patch)
        }

        fun bumpVersion(
            version: String,
            bumpType: String,
        ): String {
            val (major, minor, patch) = parseVersion(version)
            return when (bumpType) {
                "major" -> "${major + 1}.0.0"
                "minor" -> "$major.${minor + 1}.0"
                "patch" -> "$major.$minor.${patch + 1}"
                else -> "$major.$minor.${patch + 1}"
            }
        }

        fun getLatestVersion(): String {
            // Query git tags sorted by version (newest first)
            val output = exec("git", "tag", "-l", "v*", "--sort=-version:refname", "--merged")
            val latestTag = output.lines().firstOrNull() ?: return "0.0.0"
            return latestTag.removePrefix("v")
        }

        fun getLastDeployedVersions(limit: Int = 5): List<String> {
            // Query git tags sorted by version (newest first)
            val output = exec("git", "tag", "-l", "v*", "--sort=-version:refname", "--merged")
            return output
                .lines()
                .take(limit)
                .map { it.removePrefix("v") }
                .filter { it.isNotEmpty() }
        }

        fun getHighestRcNumber(baseVersion: String): Int {
            val output = exec("git", "tag", "-l", "v$baseVersion-rc.*", "--sort=-version:refname")
            val rcPattern = Regex("v$baseVersion-rc\\.(\\d+)")
            return output
                .lines()
                .mapNotNull {
                    rcPattern
                        .find(it)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                }.maxOrNull() ?: 0
        }

        fun askVersionType(): Boolean {
            println()
            print("What type of release?", "yellow")
            println("  1) Release Candidate (RC)")
            println("  2) Final Release ${colors["dim"]}[default]${colors["reset"]}")
            println()
            val choice = prompt("Choose (1-2, default 2): ") ?: "2"
            return choice.trim() == "1"
        }

        fun askVersionBump(): String {
            println()
            print("What type of version bump?", "yellow")
            println("  1) Major (breaking changes)")
            println("  2) Minor (new features) ${colors["dim"]}[default]${colors["reset"]}")
            println("  3) Patch (bug fixes)")
            println()
            val choice = prompt("Choose (1-3, default 2): ") ?: "2"
            return when (choice.trim()) {
                "1" -> "major"
                "3" -> "patch"
                else -> "minor"
            }
        }

        printHeader("Purge-MapStruct Deploy${if (dryRun) " ${colors["dim"]}[DRY RUN]${colors["reset"]}" else ""}")

        try {
            // Fetch and display current versions
            print("ℹ Fetching deployment information...", "blue")
            val currentVersion = getLatestVersion()
            print("✓ Current version: v$currentVersion", "green")

            val shouldBeRc = if (isRC) true else askVersionType()

            if (shouldBeRc) {
                // Release Candidate flow
                println()
                print("Last deployed versions:", "yellow")
                val lastVersions = getLastDeployedVersions()
                if (lastVersions.isNotEmpty()) {
                    lastVersions.forEach { print("  → v$it", "dim") }
                } else {
                    print("  → None yet", "dim")
                }

                println()
                val bumpType = askVersionBump()
                print("✓ Selected: $bumpType bump", "green")

                val newBaseVersion = bumpVersion(currentVersion, bumpType)
                print("✓ New base version: v$newBaseVersion", "green")

                val highestRc = getHighestRcNumber(newBaseVersion)
                val newRcNumber = highestRc + 1
                val newVersion = "$newBaseVersion-rc.$newRcNumber"

                println()
                print("ℹ Creating RC tag: v$newVersion", "blue")

                if (!dryRun) {
                    exec("git", "tag", "v$newVersion")
                    exec("git", "push", "origin", "v$newVersion")
                    print("✓ RC tag v$newVersion created and pushed", "green")
                } else {
                    print("${colors["dim"]}[DRY RUN] Would create and push RC tag v$newVersion${colors["reset"]}")
                }
            } else {
                // Final Release flow
                println()
                val bumpType = askVersionBump()
                print("✓ Selected: $bumpType bump", "green")

                val newVersion = bumpVersion(currentVersion, bumpType)
                print("✓ New version: v$newVersion", "green")

                println()
                print("ℹ Creating final release tag and GitHub release: v$newVersion", "blue")

                if (!dryRun) {
                    exec("git", "tag", "v$newVersion")
                    exec("git", "push", "origin", "v$newVersion")
                    exec("gh", "release", "create", "v$newVersion", "--generate-notes")
                    print("✓ Release v$newVersion created and pushed", "green")
                } else {
                    print("${colors["dim"]}[DRY RUN] Would create final release v$newVersion${colors["reset"]}")
                }
            }

            println()
            print("GitHub will trigger the pipeline automatically", "green")
            println()
        } catch (e: Exception) {
            print("✗ Error: ${e.message}", "red")
            throw e
        }
    }
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
        description = "Testing purging mapstruct from Java $version code"
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

// ---------------------------------------------------------------------------
// IntelliJ workspace configuration
// workspace.xml is gitignored (transient per-user state). This task writes the
// one setting we care about — "Optimize Imports on Save for all file types" —
// into workspace.xml so it is applied automatically on every fresh checkout
// without committing the file.
// ---------------------------------------------------------------------------
val configureWorkspace by tasks.registering {
    group = "idea"
    description = "Writes OptimizeOnSaveOptions into .idea/workspace.xml"
    doLast {
        val workspaceFile = rootDir.resolve(".idea/workspace.xml")
        val component =
            """
            <component name="OptimizeOnSaveOptions">
                <option name="myRunOnSave" value="false" />
            </component>
            """.trimIndent()
        if (!workspaceFile.exists()) {
            workspaceFile.parentFile.mkdirs()
            workspaceFile.writeText(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n  $component\n</project>\n",
            )
        } else if ("OptimizeOnSaveOptions" !in workspaceFile.readText()) {
            workspaceFile.writeText(workspaceFile.readText().replace("</project>", "  $component\n</project>"))
        }
    }
}

idea {
    project {
        this as ExtensionAware
        configure<ProjectSettings> {
            this as ExtensionAware
            configure<TaskTriggersConfig> {
                afterSync(configureWorkspace.get())
            }
        }
    }
}

dependencyLocking {
    lockAllConfigurations()
}

gradle.taskGraph.whenReady {
    if (this.allTasks.any<Task> { it.name.contains("ToMavenLocal") }) {
        tasks.withType<Sign>().configureEach {
            enabled = false
        }
    }
}
