plugins {
    id("org.openrewrite.build.recipe-library-base") version "latest.release"
    id("org.openrewrite.build.publish") version "latest.release"
    id("org.openrewrite.build.recipe-repositories") version "latest.release"
    kotlin("jvm") version "1.9.24"
}


// Set as appropriate for your organization
group = "com.santunioni.recipes"
description = "Remove Mapstruct"
version = "0.6.0-SNAPSHOT"

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

    // MapStruct for testing RemoveMapstruct recipe and fixtures
    implementation("org.mapstruct:mapstruct:latest.release")
    annotationProcessor("org.mapstruct:mapstruct-processor:latest.release")
    testImplementation("org.mapstruct:mapstruct:latest.release")
    annotationProcessor("org.mapstruct:mapstruct-processor:latest.release")
}

signing {
    // To enable signing have your CI workflow set the "signingKey" and "signingPassword" Gradle project properties
    isRequired = false
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
