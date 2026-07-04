# AGENTS.md

Guidance for agents (and humans) working on this repository.

## What this project is

`purge-mapstruct` is an [OpenRewrite](https://docs.openrewrite.org/) recipe library. It publishes
three recipes that *inline* MapStruct mappers: replacing a `@Mapper` interface/abstract class with
the plain Java code that MapStruct generated for it, then deleting the generated `*Impl` source.

The motivation is in `README.md`: MapStruct hides mapping logic behind annotations and field-name
matching, which fails silently (nulls in production). This recipe gives you back ordinary,
compile-checked Java that is *yours to change*.

## Recipes

| Recipe | Class | What it does |
| --- | --- | --- |
| `PurgeMapstruct` | `PurgeMapstruct.kt` | **Recommended.** Inlines mappers and applies targeted cleanup after inlining — but only on the files it changes. |

`PurgeMapstruct` applies cleanup visitors to files that `MapperProcessor` actually modified
(detected via object-identity `result === tree`).

`rewrite-static-analysis` and `rewrite-spring` are bundled as `implementation` dependencies, so
consumers only need to declare `purge-mapstruct` itself on the rewrite classpath.

## How the recipes work

Both are `ScanningRecipe<Accumulator>` — two passes:

1. **Scan pass** (`ImplementationScanner`): visits every compilation unit, finds
   MapStruct-generated implementations (detected via `@Generated` carrying `org.mapstruct`), and
   records the link `super interface FQN → impl CompilationUnit` (and reverse) in `Accumulator`.

2. **Edit pass** (`MapperProcessor`, a `JavaVisitor`): for each `@Mapper` declaration file it:
   - finds the single linked generated impl,
   - merges imports, copies the impl's methods (renaming the constructor, stripping `@Override`),
     copies default/static methods and static fields from the interface (un-`default`-ing them),
     strips MapStruct/`@Generated` annotations,
   - renames the impl class to the original mapper name, drops `implements`/`extends`,
   - writes the result back onto the **original mapper file's** source path + id.
   - Rewrites references everywhere else: imports, `new FooMapperImpl()`, variable/parameter
     types, `FooMapperImpl.class` field accesses, and `instanceof` checks.
   - Returns `null` when it encounters the generated impl file itself, **deleting** it.

### Source files (`src/main/java/io/github/santunioni/recipes/`)

| File | Responsibility |
| --- | --- |
| `PurgeMapstruct.kt` | Recipe: wires scanner + `MapperProcessor`. |
| `removeMapstruct/Accumulator.kt` | Shared state between passes: the super↔impl linkings. |
| `removeMapstruct/ImplementationScanner.kt` | Scan pass — records linkings. |
| `removeMapstruct/MapperProcessor.kt` | Edit pass — does the merge, reference rewrites, impl deletion, and targeted cleanup. |
| `removeMapstruct/Functions.kt` | `isMapperImplementation` / `isMapperDeclaration` detection helpers. |
| `removeMapstruct/StatementDefinitionOrder.kt` | Comparator that orders the merged class members sensibly. |

There is no `rewrite.yml`. Formatting is handled entirely within `MapperProcessor` via
`AutoFormat(null)` (OpenRewrite default style).

## Philosophy / conventions

- **Source language**: recipe source is **Kotlin** (`.kt`). Java fixtures in `src/test/resources/`
  are still `.java` — that is intentional; they are test inputs, not recipe source.
- **Null-safety**: use `org.jspecify.annotations.@Nullable` for nullables. Returning `null` from a
  visitor's `visit` / `visitCompilationUnit` is how you delete a file in OpenRewrite.
- **Fail loud, recover gracefully**: `MapperProcessor` logs `severe` and rethrows on unexpected
  merge errors; it *skips* (leaves code untouched) when it can't find exactly one implementer.
- **Work on the LST, not strings**: manipulate OpenRewrite `J.*` tree nodes with `withX(...)` and
  `ListUtils`; preserve `Space`/prefixes so formatting survives.
- **Targeted cleanup**: `PurgeMapstruct` uses object-identity (`result === tree`) to detect whether
 `MapperProcessor` changed a file before applying cleanup visitors to it. Do not
 break this guard — it is what keeps unrelated files untouched.
- Java 17 source/target, Java toolchain 17.

## Developing

- **Trunk-based development**: commit and push directly to `main`. No feature-branch / PR flow.
  Keep commits small and self-contained so the trunk stays releasable.
- **Babysit CI after every push**: watch the pipeline after each push; fix failures immediately.
- Java 17 toolchain (see `.sdkmanrc` — `sdk env` to match). Gradle wrapper is committed.
- The BOM is pinned to a specific version in `build.gradle.kts` (not `latest.release`) to ensure
  reproducible builds for consumers. Update it deliberately.
- Build: `./gradlew build`
- The main loop you'll iterate on is `MapperProcessor.kt`. When changing transformation behavior,
  prefer adding/adjusting a fixture-based test over reasoning in the abstract — OpenRewrite LST
  behavior is easy to get subtly wrong on spacing/types.

## Testing

Tests use OpenRewrite's `RewriteTest` harness with the `org.openrewrite.java.Assertions.java(...)`
DSL. The suite lives in `src/test/java/io/github/santunioni/recipes/PurgeMapstructTest.kt`.

- Run all tests: `./gradlew test`
- Run one test: `./gradlew test --tests "io.github.santunioni.recipes.PurgeMapstructTest.<method>"`
- The recipe under test is configured in `defaults(RecipeSpec)`: it runs `PurgeMapstruct()` with
  `mapstruct`, `lombok`, and `junit-jupiter-api` on the parser classpath.

### Work test-first (TDD)

**Always develop changes to this recipe test-first.** OpenRewrite LST transformations are easy to
get subtly wrong (spacing, types, member order), so the test fixture is how you discover what the
recipe actually produces before you trust it.

1. **Write the failing test first.** Create the `before/` input and a placeholder `after/` (e.g.
   just `PLACEHOLDER`). Wire up the `@Test` and run it.
2. **Let the test print the real output.** The assertion fails with a `but was:` block — that is
   ground truth for what the recipe does today.
3. **Decide if the output is correct.** If yes, copy the `but was:` content verbatim into `after/`.
   If not, fix the recipe and repeat.
4. **Run the full suite** (`./gradlew test`) to check for regressions before committing.

### Fixture layout

Each test reads `.java` fixtures from `src/test/resources/fixtures/<testName>/`:

```
fixtures/<testName>/
  context/   # files needed for parsing/linking but not asserted on
             # — DTOs, entities, AND the generated *MapperImpl.java
  before/    # input state of the file(s) under assertion
  after/     # expected output, filename-matched to before/
```

Conventions:

- One file per role, named after the type (e.g. `before/CustomerMapper.java` ↔ `after/CustomerMapper.java`).
- `context/` holds supporting types plus the MapStruct-generated `*MapperImpl.java`. The generated
  impl must carry `@Generated(value = "org.mapstruct...")` so the scanner recognises it.
- Fixtures are loaded with `readResource("fixtures/<testName>/<role>/<File>.java")`.

A test wires fixtures to virtual source paths via `spec.path(...)`. **The path matters**: generated
impls live under `build/generated/annotationProcessor/main/java/...` and real sources under
`src/main/java/...`. The generated impl's `after` is `null` (it gets deleted); the `@Mapper`
file's `before`→`after` shows the inlined result.

### Asserting the generated impl is deleted

```kotlin
java(
    readResource(".../context/UserMapperImpl.java"),
    null as String?,   // expect this file to be deleted
) { spec ->
    spec.path("build/generated/annotationProcessor/main/java/.../UserMapperImpl.java")
}
```

### Adding a new test case (TDD order)

1. Create `fixtures/<newCase>/` with `context/`, `before/`, and `after/` containing `PLACEHOLDER`.
2. Add a `@Test` method following existing ones as templates; wire each fixture with `spec.path(...)`.
3. Run just that test — read the `but was:` output.
4. Verify the output is correct (fix the recipe if not), paste into `after/`.
5. `./gradlew test` to confirm green with no regressions.

---

## Testing the recipe against a real project

The unit-test harness catches many issues, but the definitive validation is running the recipe on a
real codebase. This section documents the setup steps and common pitfalls.

### Publish a local snapshot

`publishToMavenLocal` fails without GPG keys because signing is configured for all publications.
The `nebula.release` snapshot strategy skips signing automatically, so use:

```bash
./gradlew snapshot publishNebulaPublicationToMavenLocal
```

This produces version `x.y.z-SNAPSHOT` in `~/.m2/repository/io/github/santunioni/purge-mapstruct/`.

**Do NOT use `includeBuild`** to wire the recipe into the target project. This recipe requires
Gradle 9+, but target projects often run an older wrapper version. Gradle rejects composite builds
between incompatible versions.

### Wire the recipe into the target project's build

```groovy
plugins {
    id 'org.openrewrite.rewrite' version '7.35.0'
}

repositories {
    mavenLocal()
    maven { url "https://central.sonatype.com/repository/maven-snapshots/" }
}

dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:0.2.0-SNAPSHOT"
    // rewrite-static-analysis and rewrite-spring are bundled transitively — no extra deps needed
}

rewrite {
    activeRecipe("io.github.santunioni.recipes.PurgeMapstruct")
    throwOnParseFailures = false   // recommended for large codebases with generated files
}
```

Also redirect annotation-processor output so generated `*Impl` files land where OpenRewrite scans:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.generatedSourceOutputDirectory = file("$projectDir/src/generated/java")
}
sourceSets {
    main { java.srcDirs += "$projectDir/src/generated/java" }
}
```

### Dependency locking

Adding the `rewrite` configuration introduces new dependencies. Update locks as needed:

```bash
./gradlew resolveAndLockAll --write-locks --refresh-dependencies
```

### Memory

`rewriteRun` parses every source file in memory. Add to `gradle.properties` in the target project:

```properties
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=512m
```

### The iteration loop

```
1. git checkout -- src/ && rm -rf src/generated/   # revert to HEAD, clear generated sources
2. ./gradlew --stop                                 # clear Gradle daemon VFS
3. ./gradlew compileJava compileTestJava            # regenerate MapStruct *Impl files
4. ./gradlew rewriteRun
5. ./gradlew --stop                                 # mandatory — daemon VFS still references deleted *Impl files
6. ./gradlew compileJava compileTestJava            # verify no compile errors
7. If errors → fix the recipe, publish new snapshot, goto 1
```

**Why `--stop` before step 6?** `rewriteRun` deletes the generated `*Impl.java` files. The Gradle
daemon keeps an in-memory VFS that still references those paths. Without stopping it, the next
`compileJava` fails with `Failed to normalize content of '...Impl.java'`.

---

## Releasing

Versioning is managed by the `nebula.release` Gradle plugin. CI (`.github/workflows/pipeline.yml`)
handles the actual publication to Sonatype; you only push a tag.

### Release candidate

```bash
git tag v<major>.<minor>.<patch>-rc.<N>
git push origin v<major>.<minor>.<patch>-rc.<N>
```

Example: `git tag v0.2.0-rc.5 && git push origin v0.2.0-rc.5`

CI detects the `-rc.` tag and runs:
```
./gradlew candidate publish closeAndReleaseSonatypeStagingRepository
```
The artifact lands on Maven Central as a signed, versioned RC.

### Final release

```bash
git tag v<major>.<minor>.<patch>
git push origin v<major>.<minor>.<patch>
```

Example: `git tag v0.2.0 && git push origin v0.2.0`

CI detects a plain version tag (no `-rc.`) and runs:
```
./gradlew final publish closeAndReleaseSonatypeStagingRepository
```

### Snapshot (every push to `main`)

CI automatically publishes a snapshot on every push to `main` — no tag required.
To publish a snapshot locally (e.g. to test in a target project):

```bash
./gradlew snapshot publishNebulaPublicationToMavenLocal
```

### Choosing the next version

Check the latest tag to determine the correct next version:

```bash
git tag | sort -V | tail -5
```
