# AGENTS.md

Guidance for agents (and humans) working on this repository.

## What this project is

`purge-mapstruct` is an [OpenRewrite](https://docs.openrewrite.org/) recipe library. It publishes a
single recipe that *inlines* MapStruct mappers: replacing a `@Mapper` interface/abstract class with
the plain Java code that MapStruct generated for it, then deleting the generated `*Impl` source.

The motivation is in `README.md`: MapStruct hides mapping logic behind annotations and field-name
matching, which fails silently (nulls in production). This recipe gives you back ordinary,
compile-checked Java that is *yours to change*.

## Recipe

| Recipe | Class | What it does |
| --- | --- | --- |
| `PurgeMapstruct` | `PurgeMapstruct.kt` | Inlines every `@Mapper` into plain Java and applies a curated cleanup/formatting pass — but only to the files it changes. |

`rewrite-static-analysis` and `rewrite-spring` are bundled as `implementation` dependencies, so
consumers only need to declare `purge-mapstruct` itself on the rewrite classpath.

## How the recipe works

`PurgeMapstruct` is a `ScanningRecipe<MapstructRefs>` with two passes:

1. **Scan pass** (`MappersGathererScanner`, a `JavaIsoVisitor`): visits every compilation unit, finds
   MapStruct-generated implementations (detected by `isMapperImplementation` — a class that
   `implements`/`extends` something and carries a `javax.annotation.processing.Generated` annotation
   whose `value` starts with `org.mapstruct`), and records the linkings `super FQN → impl
   CompilationUnit` and the reverse `impl FQN → super FQN` into `MapstructRefs` via `addLinking`. The
   scanner only ever writes through the `MapstructRefsWriter` interface.

2. **Edit pass** (`InlineMapstructPipeline`, a `JavaVisitor`): it overrides `visit(tree, ctx)` and, for
   each compilation unit, runs an ordered set of small visitors around the core merge (`InlineMapstruct`).
   It reads scan-pass state only through the `MapstructRefsReader` interface:

   - **`preApplyToAll`** — broad rewrites whose result is kept even when the file is never inlined
     (unrelated call sites, spy stubs in test files):
     - `ReplaceMappersGetMapper` — `Mappers.getMapper(X.class)` → `new X()`.
     - `RewriteWhenOnSpy` — `when(spy.x(a)).thenReturn(v)` → `doReturn(v).when(spy).x(a)`.
     - `DeleteMapperImplementations` — returns `null` for generated `*Impl` files, **deleting** them
       (when any visitor in this list returns `null`, the pipeline returns `null` for that file).
     - `RewriteImplReferences` — rewrites `*Impl` references back to the mapper type everywhere
       (instance-scoped: constructor-injected with `MapstructRefsReader`).
   - **`preApplyToTouchedFiles`** — only meaningful as merge preparation; rolled back if the merge
     doesn't change the file, so non-inlined files stay pristine:
     - `FullyQualifyTypesInImplementation` — fully-qualifies types inside impl files before copy.
   - **The merge** (`InlineMapstruct`) — builds the inlined class for each `@Mapper` file (below).
   - **`postApplyToTouchedFiles`** — applied only to files that actually changed:
     - `RewriteImplReferences` — rewrites `*Impl` references copied in from the impl body.
     - `StripMapstructAnnotations` — removes `org.mapstruct` annotations from the merged class.
     - `ReplaceMappersGetMapper` — catches `Mappers.getMapper` copied in from the impl body.
     - Cleanup/formatting pack: `UnnecessaryParentheses`, `RemoveUnusedLocalVariables`,
       `RemoveUnusedImports`, `LambdaBlockToExpression`, `ReplaceLambdaWithMethodReference`,
       `NoAutowiredOnConstructor`, `InlineVariable` (×3), `AutoFormat(null)`, `CodeCleanup`
       (loaded lazily from the runtime classpath via `Environment`), `ShortenFullyQualifiedTypeReferences`.

   The "only touch changed files" behavior is an object-identity guard: the pipeline keeps the merged
   result when `inlined !== afterConditional`, otherwise falls back to the broad `afterAlways` result;
   and it returns early without cleanup when `changed === original`. Do not break this guard — it is
   what keeps unrelated files untouched.

### The merge (`InlineMapstruct.visitCompilationUnit`)

For each `@Mapper` declaration file it:
- finds the single linked generated impl (`getImplementer`; **skips**, leaving code untouched, if it
  can't find exactly one),
- copies the impl's methods (renaming the constructor, stripping `@Override`),
- copies the declaration's `default`/`static` methods (un-`default`-ing them) and interface fields
  (as `public static final`),
- keeps `@Generated`-filtered annotations, renames the impl class to the original mapper name, drops
  `implements`/`extends`, and strips `org.mapstruct` imports,
- writes the result back onto the **original mapper file's** source path + id.

The merge builds only the *raw* inlined class. Reference rewriting (`new FooMapperImpl()`,
`FooMapperImpl.class`, variable/parameter types, `instanceof`, imports) and MapStruct annotation
stripping are **not** done in the merge — they are pipeline post visitors (`RewriteImplReferences`,
`StripMapstructAnnotations`), mirroring how `ReplaceMappersGetMapper` also runs in the pipeline.

### Decorated mappers (`@DecoratedWith`)

A `@Mapper @DecoratedWith(FooMapperDecorator.class)` interface is a **four-body** structure: the
interface, the hand-written `FooMapperDecorator`, the `@Primary` `FooMapperImpl` (`extends` the
decorator) and the delegate `FooMapperImpl_` (`@Qualifier("delegate")`, `implements` the interface).
`InlineMapstruct` skips these (its single-implementer merge would silently drop the decorator's
behaviour). Instead `InlineDecoratedMapper` collapses all four into **one concrete class** written
onto the interface's path:

- the outer `FooMapper` = the decorator (overridden methods + helpers) + the primary impl's
  non-overridden `return delegate.x(...)` pass-throughs, made concrete, `@Component`;
- the delegate impl becomes a nested `static FooMapperDelegate` class (drops `implements` + the
  `@Qualifier` marker, keeps `@Component`);
- the `@Autowired @Qualifier("delegate") FooMapper delegate` field is retyped to the nested
  `FooMapperDelegate` (no `@Qualifier`), so every `delegate.x(...)` call resolves by type — the exact
  runtime call structure MapStruct produced is preserved.

The scanner records every CU by FQN plus each decorator FQN (`MapstructRefs`), so the merge can locate
the decorator + both impls by name. `DeleteMapperDecorators` deletes the standalone decorator file
(the generated impls are deleted by `DeleteMapperImplementations` as usual). Imports are merged from
all four sources (minus `org.mapstruct`) and de-duplicated; `RemoveUnusedImports` prunes the rest.

### Source files (`src/main/java/io/github/santunioni/recipes/`)

The code is organised by role: the `inlineMapstruct` package holds shared state and the pipeline,
its `scanners` sub-package the scan pass, and its `recipes` sub-package the individual edit visitors.

| File | Responsibility |
| --- | --- |
| `PurgeMapstruct.kt` | The recipe (top-level `public`): wires `MappersGathererScanner` (scanner) + `InlineMapstructPipeline` (visitor). |
| `inlineMapstruct/MapstructRefs.kt` | Shared scan-pass state (super↔impl linkings). The concrete `public` class implements both `MapstructRefsWriter` (scan) and `MapstructRefsReader` (edit). |
| `inlineMapstruct/InlineMapstructPipeline.kt` | Edit pass — orchestrates the pre/merge/post visitor lists per file and the object-identity change guard. |
| `inlineMapstruct/Functions.kt` | `isMapperImplementation` / `isMapperDeclaration` / `isDecoratedMapperDeclaration` / `getDecoratorFqn` detection helpers. |
| `inlineMapstruct/scanners/MappersGathererScanner.kt` | Scan pass — records linkings via `MapstructRefsWriter`. |
| `inlineMapstruct/scanners/MapstructRefsWriter.kt` | Write-only interface exposed to the scanner (`addLinking`). |
| `inlineMapstruct/recipes/InlineMapstruct.kt` | The core merge — builds the inlined class from impl + declaration. Skips decorated (`@DecoratedWith`) mappers. |
| `inlineMapstruct/recipes/InlineDecoratedMapper.kt` | Merge for `@DecoratedWith` mappers — collapses the interface + decorator + `@Primary` impl + delegate impl into one concrete class with a nested static `*Delegate`. |
| `inlineMapstruct/recipes/DeleteMapperDecorators.kt` | Deletes the hand-written `@DecoratedWith` decorator files (reader-backed). |
| `inlineMapstruct/recipes/MapstructRefsReader.kt` | Read-only interface exposed to the edit visitors (`getImplementer`, `getSuperFqnFromImplFqn`). |
| `inlineMapstruct/recipes/RewriteImplReferences.kt` | Rewrites `*Impl` references back to the mapper type (reader-backed). |
| `inlineMapstruct/recipes/StripMapstructAnnotations.kt` | Removes `org.mapstruct` annotations wherever they appear. |
| `inlineMapstruct/recipes/DeleteMapperImplementations.kt` | Deletes generated `*Impl` files (returns `null`). |
| `inlineMapstruct/recipes/ReplaceMappersGetMapper.kt` | `Mappers.getMapper(X.class)` → `new X()`. |
| `inlineMapstruct/recipes/RewriteWhenOnSpy.kt` | `when(spy.x()).thenReturn(v)` → `doReturn(v).when(spy).x()`. |
| `inlineMapstruct/recipes/FullyQualifyTypesInImplementation.kt` | Fully-qualifies types in impl files before the merge copies them. |

There is no `rewrite.yml`. Formatting is handled entirely within `InlineMapstructPipeline` via
`AutoFormat(null)` (OpenRewrite default style). Runtime logging goes through `java.util.logging`;
`src/main/resources/logback.xml` configures the logback binding that the OpenRewrite parser uses.

## Philosophy / conventions

- **Source language**: recipe source is **Kotlin** (`.kt`). Java fixtures in `src/test/resources/`
  are still `.java` — that is intentional; they are test inputs, not recipe source.
- **Null-safety**: rely on Kotlin's nullable types (`T?`, `as?`, `?:`) rather than annotations. Note
  that OpenRewrite's `TreeVisitor.visit` is `@Nullable` on the Java side, so Kotlin sees it as
  returning `J?` — returning `null` from a visitor's `visit` / `visitCompilationUnit` is how you delete
  a file (or a tree node).
- **Small, composable visitors**: new behavior belongs in a single-purpose `JavaVisitor` under
  `inlineMapstruct/recipes/`, wired into the pipeline's `preApplyToAll` / `preApplyToTouchedFiles` /
  `postApplyToTouchedFiles` lists (constructor-inject `MapstructRefsReader` if it needs the scan-pass
  linkings), rather than piled into the merge. `InlineMapstruct` should stay focused on the merge.
- **Explicit API / minimal surface**: `explicitApi()` is enabled in `build.gradle.kts`, so every
  declaration needs an explicit visibility. Keep the published surface as small as possible — only
  `PurgeMapstruct` and `MapstructRefs` are `public`; everything else is `internal`. New visitors,
  scanners, and helpers should be `internal`.
- **Segregated scan-pass state**: `MapstructRefs` is the single concrete holder, but the scanner sees
  it only as a `MapstructRefsWriter` (write) and the edit visitors only as a `MapstructRefsReader`
  (read). Keep that split — don't hand the concrete class to a visitor.
- **Fail loud, recover gracefully**: `InlineMapstruct` logs `severe` and rethrows on unexpected merge
  errors; it *skips* (leaves code untouched) when it can't find exactly one implementer.
- **Work on the LST, not strings**: manipulate OpenRewrite `J.*` tree nodes with `withX(...)` and
  `ListUtils`; preserve `Space`/prefixes so formatting survives.
- **Targeted cleanup**: `InlineMapstructPipeline` uses object identity to detect whether a file was
  actually changed before applying the post cleanup visitors. Do not break this guard — it is what
  keeps unrelated files untouched.
- Java 17 source/target, Java toolchain 17.

## Developing

- **Trunk-based development**: commit and push directly to `main`. No feature-branch / PR flow.
  Keep commits small and self-contained so the trunk stays releasable.
- **Babysit CI after every push**: watch the pipeline after each push; fix failures immediately.
- Java 17 toolchain (see `.sdkmanrc` — `sdk env` to match). Gradle wrapper is committed.
- The BOM is pinned to a specific version in `build.gradle.kts` (not `latest.release`) to ensure
  reproducible builds for consumers. Update it deliberately.
- Build: `./gradlew build`
- The main files you'll iterate on are `InlineMapstruct.kt` (merge behavior) and
  `InlineMapstructPipeline.kt` (visitor ordering). When changing transformation behavior, prefer
  adding/adjusting a fixture-based test over reasoning in the abstract — OpenRewrite LST behavior is
  easy to get subtly wrong on spacing/types.

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
handles the actual publication to Sonatype; you only push a tag or create a release.

### Using the deploy task (recommended)

The `deploy` task automates the entire release workflow with interactive prompts:

```bash
./gradlew deploy --no-daemon
```

The task will:
1. Show the current version and last deployed versions
2. Ask you to choose: **Release Candidate (RC)** or **Final Release**
3. Ask for version bump type: **Major**, **Minor** (default), or **Patch**
4. Create the tag and (for finals) the GitHub release automatically
5. Push the tag, which triggers CI to publish

**Flags:**
- `--no-daemon` — Required for interactive input (enables stdin)
- `-PdryRun=true` — Preview the deployment without making changes

**Examples:**
```bash
# Interactive final release with prompts
./gradlew deploy --no-daemon

# Preview RC deployment without making changes
./gradlew deploy --no-daemon -PdryRun=true -Prc=true
```

### Release candidate (manual)

If you prefer manual control:

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

### Final release (manual)

If you prefer manual control:

```bash
git tag v<major>.<minor>.<patch>
git push origin v<major>.<minor>.<patch>
gh release create v<major>.<minor>.<patch> --generate-notes
```

Example:
```bash
git tag v0.2.0 && git push origin v0.2.0
gh release create v0.2.0 --generate-notes
```

CI detects a published release and runs:
```
./gradlew final publish closeAndReleaseSonatypeStagingRepository
```

### Snapshot (every push to `main`)

CI automatically publishes a snapshot on every push to `main` — no tag required.
The snapshot version is the *next* logical version from the latest tag (e.g., if the latest tag is `v0.2.2`, snapshots are published as `0.3.0-SNAPSHOT`).

To publish a snapshot locally (e.g. to test in a target project):

```bash
./gradlew snapshot publishNebulaPublicationToMavenLocal
```

### Choosing the next version

Check the latest tag to determine the correct next version:

```bash
git tag | sort -V | tail -5
```
