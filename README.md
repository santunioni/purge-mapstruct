# PurgeMapstruct

An OpenRewrite recipe that inlines MapStruct mapper implementations into plain Java code, giving you full ownership of
your mapping logic.

> **New to OpenRewrite?** OpenRewrite is an automated refactoring engine for Java (and other languages). It works by
> parsing your source files into a lossless syntax tree, applying recipes that modify that tree, and writing the changes
> back — preserving formatting and comments. Before diving into this README, skim
> the [OpenRewrite introduction](https://docs.openrewrite.org/) and the plugin setup
> for [Gradle](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
> or [Maven](https://docs.openrewrite.org/reference/rewrite-maven-plugin).
> The [recipe catalog](https://docs.openrewrite.org/recipes) is also worth bookmarking.

---

## Philosophy

MapStruct's greatest feature is its greatest flaw: it auto-maps your value objects by matching field names. When it
can't match, you either get a silently unmapped field (null in production) or you write the mapping logic as annotation
attributes.

The result is a codebase where mappings are invisible, bugs are silent, and understanding what actually happens requires
reading both the interface and the generated implementation — a file that lives in `build/`, is never committed, and
disappears on a clean build. That is not easy to maintain; it is obscurity. MapStruct optimises for writing less code, not
for reading or maintaining it.

In any long-lived codebase, code is read far more often than it is written — and the two are inseparable: every
change requires understanding what is already there. With MapStruct, that cost rises steeply as complexity grows.
Simple field-matching is manageable; but once a mapping requires logic, you are writing Java expressions inside
annotation strings, referencing methods by name in a context the LSP cannot reach, or that you need too many go-to-definition to understand, lacking coesion. Every subsequent change means
reconstructing the generated output in your head before you can touch the source. AI has made writing even cheaper,
but every line generated is still paid for in full, every time a human has to reason about it.

This recipe removes MapStruct from your project by replacing every `@Mapper` interface with its generated
implementation, plus a series of mechanical refactorings to make the gemerated code better readable. Then the code will be yours to read and improve.

> *"Software should be designed for ease of reading, not ease of writing."*
> — John Ousterhout, [A Philosophy of Software Design](https://web.stanford.edu/~ouster/cgi-bin/book.php)

---

## How to use

### Step 1: install the OpenRewrite plugin

**Gradle** — add to your root `build.gradle`:

```groovy
plugins {
    id "org.openrewrite.rewrite" version "latest.release"
}
```

> Find the latest version on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.openrewrite.rewrite).

**Maven** — add to your root `pom.xml`:

```xml

<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>LATEST</version>
</plugin>
```

> Find the latest version
> on [Maven Central](https://central.sonatype.com/artifact/org.openrewrite.maven/rewrite-maven-plugin).

### Step 2: redirect generated sources out of `build/`

The OpenRewrite plugin deliberately excludes `build/` (or `target/`) from scanning. MapStruct writes its `*Impl`
classes there by default, so you must redirect annotation processor output into `src/` first.

**Gradle** (`build.gradle`):

```groovy
tasks.withType(JavaCompile).configureEach {
    options.generatedSourceOutputDirectory = file("$projectDir/src/generated/java")
}

sourceSets {
    main {
        java.srcDirs += "$projectDir/src/generated/java"
    }
}
```

> In a multi-module project, wrap the above in `subprojects { }` in the root `build.gradle`.

**Maven** (`pom.xml`):

```xml

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <generatedSourcesDirectory>${project.basedir}/src/generated/java</generatedSourcesDirectory>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>add-generated-sources</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-source</goal>
                    </goals>
                    <configuration>
                        <sources>
                            <source>${project.basedir}/src/generated/java</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Step 3: normalise formatting first (Spotless)

Run your formatter across the whole codebase **before** the recipe. This keeps the inlining diff
clean: when you run the formatter again after the recipe, only the files the recipe touched will
show up as changed — everything else is already formatted and stays untouched.

**Gradle:**

```groovy
plugins {
    id "com.diffplug.spotless" version "latest.release"
}

spotless {
    java {
        googleJavaFormat() // or palantirJavaFormat(), eclipse(), etc.
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

Run with `./gradlew spotlessApply`.
See [Spotless Gradle docs](https://github.com/diffplug/spotless/tree/main/plugin-gradle).

**Maven:**

```xml

<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>LATEST</version>
    <configuration>
        <java>
            <googleJavaFormat/>
            <removeUnusedImports/>
            <trimTrailingWhitespace/>
            <endWithNewline/>
        </java>
    </configuration>
</plugin>
```

Run with `./mvnw spotless:apply`.
See [Spotless Maven docs](https://github.com/diffplug/spotless/tree/main/plugin-maven).

### Step 4: run the recipe

Use `io.github.santunioni.recipes.PurgeMapstruct`. It inlines every `@Mapper` into plain Java and applies cleanup (unused imports, redundant parens, lambda simplification, formatting) — but only to the files it changes, keeping the diff small.

**Gradle** (`build.gradle`):

```groovy
dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:latest.release"
}

rewrite {
    activeRecipe("io.github.santunioni.recipes.PurgeMapstruct")
}
```

**Maven** (`pom.xml`, inside the `rewrite-maven-plugin` configuration):

```xml

<configuration>
    <activeRecipes>
        <recipe>io.github.santunioni.recipes.PurgeMapstruct</recipe>
    </activeRecipes>
</configuration>
<dependencies>
<dependency>
    <groupId>io.github.santunioni</groupId>
    <artifactId>purge-mapstruct</artifactId>
    <version>LATEST</version>
</dependency>
</dependencies>
```

Compile first so MapStruct generates the `*Impl` files, run the recipe, then verify:

```bash
# Gradle
./gradlew compileJava compileTestJava \
  && ./gradlew rewriteRun \
  && ./gradlew compileJava compileTestJava test

# Maven
./mvnw compile test-compile \
  && ./mvnw rewrite:run \
  && ./mvnw compile test-compile test
```

> **Note on Mockito `@Spy`:** If your tests spy on mapper fields using `when(myMapper.someMethod(...)).thenReturn(...)`,
> those stubs will break after inlining because the mapper is now a concrete class. The recipe automatically rewrites
> them to `doReturn(...).when(myMapper).someMethod(...)`, which is the correct pattern for concrete-class spies.

After the recipe runs, apply the formatter again — now only the inlined mapper files will be reformatted:

```bash
./gradlew spotlessApply   # or ./mvnw spotless:apply
```

---

## Unsupported features

In the [TODO file](TODO.md) I am registering usages of MapStruct that the recipe currently doesn't support. I intend to
cover them in future releases. But in the meantime, you can use the recipe if it is possible for you to remove those
complex usages from your codebase manually as a pre-work.

---

## Feedback

I iterated on this recipe until it successfully purged MapStruct from two large production codebases. But I don't know
your patterns. Maybe you are doing something I didn't cover.

If the recipe fails, produces broken code, or leaves something
behind — [open an issue](https://github.com/santunioni/purge-mapstruct/issues/new) on GitHub with a minimal reproducer
and I'll look into it.

---

## Further reading

- [OpenRewrite documentation](https://docs.openrewrite.org/)
- [OpenRewrite recipe catalog](https://docs.openrewrite.org/recipes)
- [Gradle plugin configuration](https://docs.openrewrite.org/reference/gradle-plugin-configuration)
- [Maven plugin configuration](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
- [Spotless Gradle plugin](https://github.com/diffplug/spotless/tree/main/plugin-gradle)
- [Spotless Maven plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven)
