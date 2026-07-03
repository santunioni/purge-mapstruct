# AGENTS.md

Guidance for agents (and humans) working on this repository.

## What this project is

`purge-mapstruct` is a single [OpenRewrite](https://docs.openrewrite.org/) recipe library. It
publishes one recipe, **`PurgeMapstruct`**, that *inlines* MapStruct mappers: it replaces a
`@Mapper` interface/abstract class with the plain Java code that MapStruct generated for it, then
deletes the generated `*Impl` source.

The motivation is in `README.md`: MapStruct hides mapping logic behind annotations and field-name
matching, which fails silently (nulls in production). This recipe gives you back ordinary,
compile-checked Java that is *yours to change* — even if the first output is ugly. The intended
workflow is: run quality recipes (CodeCleanup, Spring, etc.) before and after `PurgeMapstruct` to
polish the generated code.

## How the recipe works

`PurgeMapstruct` is an OpenRewrite `ScanningRecipe<Accumulator>` — it runs in two passes:

1. **Scan pass** (`ImplementationScanner`): visits every compilation unit, finds the
   MapStruct-generated implementations (detected via the `@Generated` annotation carrying
   `org.mapstruct`), and records the link `super interface FQN -> impl CompilationUnit` (and the
   reverse `impl FQN -> super FQN`) in the `Accumulator`.

2. **Edit pass** (`MapperProcessor`, a `JavaVisitor`): for each `@Mapper` declaration file it:
   - finds the single linked generated impl,
   - merges imports, copies the impl's methods (renaming the constructor, stripping `@Override`),
     copies default/static methods and static fields from the interface (un-`default`-ing them),
     strips MapStruct/`@Generated` annotations,
   - renames the impl class to the original mapper name, drops `implements`/`extends`,
   - writes the result back onto the **original mapper file's** source path + id.
   - It also rewrites references everywhere else: imports, `new FooMapperImpl()`, variable/parameter
     types, `FooMapperImpl.class` field accesses, and `instanceof` checks — all `...Impl` -> the
     original name.
   - Finally, when it encounters the generated impl compilation unit itself, it returns `null` to
     **delete** that now-redundant source file.

### Source files (`src/main/java/com/santunioni/recipes/`)

| File | Responsibility |
| --- | --- |
| `PurgeMapstruct.java` | The recipe entrypoint; wires scanner + visitor. Class Javadoc documents the full behavior. |
| `removeMapstruct/Accumulator.java` | Shared state between passes: the super<->impl linkings. |
| `removeMapstruct/ImplementationScanner.java` | Scan pass — records linkings. |
| `removeMapstruct/MapperProcessor.java` | Edit pass — does the merge, reference rewrites, and impl deletion. |
| `removeMapstruct/Functions.java` | `isMapperImplementation` / `isMapperDeclaration` detection helpers. |
| `removeMapstruct/StatementDefinitionOrder.java` | Comparator that orders the merged class members sensibly. |

`src/main/resources/META-INF/rewrite/rewrite.yml` defines the `AutoFormatRecipeOutputForTest` style
used only to format test output. The recipe is registered/published as
`io.github.santunioni` group, version in `build.gradle.kts`.

## Philosophy / conventions

- **Null-safety**: code is `@NullMarked` (JSpecify); use `org.jspecify.annotations.@Nullable` for
  nullables. Returning `@Nullable J` from a visitor's `visitCompilationUnit` is how you delete a file.
- **Fail loud, recover gracefully**: `MapperProcessor` logs `severe` and rethrows on unexpected
  merge errors; it *skips* (leaves code untouched) when it can't find exactly one implementer.
- **Work on the LST, not strings**: manipulate OpenRewrite `J.*` tree nodes with `withX(...)` and
  `ListUtils`; preserve `Space`/prefixes so formatting survives. Reach for `AutoFormat` for cleanup
  rather than hand-spacing where possible.
- Java 17 source/target, Java toolchain 17. Kotlin is configured but currently unused for source.

## Developing

- **Trunk-based development**: this project commits and pushes directly to `main`. There is no
  feature-branch / PR flow here — make your change, ensure `./gradlew test` is green, commit to
  `main`, and `git push`. Keep commits small and self-contained so the trunk stays releasable.
- **Babysit CI after every push**: pushing to `main` triggers the pipeline. After each push, watch
  the run until it goes green. If it fails, fix it (don't leave the trunk red) — push follow-up
  commits until CI is green again.
- Java 17 toolchain (see `.sdkmanrc` — `sdk env` to match). Gradle wrapper is committed.
- Dependencies resolve to `latest.release` against the OpenRewrite BOM, so a network fetch is needed
  on first build.
- Build: `./gradlew build`
- The main loop you'll iterate on is `MapperProcessor`. When changing transformation behavior,
  prefer adding/adjusting a fixture-based test (below) over reasoning in the abstract — OpenRewrite
  LST behavior is easy to get subtly wrong on spacing/types.

## Testing

Tests use OpenRewrite's `RewriteTest` harness with the `org.openrewrite.java.Assertions.java(...)`
DSL. The suite lives in `src/test/java/com/santunioni/recipes/PurgeMapstructTest.java`.

- Run all tests: `./gradlew test`
- Run one test: `./gradlew test --tests "com.santunioni.recipes.PurgeMapstructTest.<method>"`
- The recipe under test is configured in `defaults(RecipeSpec)`: it runs `PurgeMapstruct` followed
  by `AutoFormat`, with `mapstruct`, `lombok`, and `junit-jupiter-api` on the parser classpath.

### Work test-first (TDD)

**Always develop changes to this recipe test-first.** OpenRewrite LST transformations are easy to
get subtly wrong (spacing, types, member order), so the test fixture is how you discover what the
recipe actually produces before you trust it. This is exactly how the
`shouldRemoveAfterMappingDecorators` case (the `@AfterMapping`/`@MappingTarget` feature) was built:

1. **Write the failing test first.** Create the `before/` input that exercises the new behavior and
   a *placeholder* `after/` file (e.g. just `PLACEHOLDER`). Wire up the `@Test` and run it.
2. **Let the test print the real output.** The assertion fails with a `but was:` block showing the
   recipe's actual output. Read it — that is ground truth for what the recipe does today.
3. **Decide if the output is correct.** If it already does the right thing, copy the `but was:`
   content verbatim into `after/` and you have a green regression test. If it's wrong, that block is
   your bug reproduction — now go fix the recipe.
4. **Fix the recipe, re-run, iterate** until the real output matches a hand-verified-correct
   `after/`. Never hand-write the expected output from imagination and assume the recipe matches it;
   confirm it empirically.
5. **Run the full suite** (`./gradlew test`) to check for regressions before committing.

A scratch test with inline source strings is fine for *exploring* behavior quickly, but the
committed test must be fixture-based (below), matching the existing cases.

### Fixture layout

Each test reads `.java` fixtures from `src/test/resources/fixtures/<testName>/`:

```
fixtures/<testName>/
  context/   # files that must exist for parsing/linking but whose final state we don't assert
             # — DTOs, entities, AND the generated *MapperImpl.java (the recipe needs the generated
             #   impl in context to do the merge)
  before/    # input state of the file(s) we assert on — typically the @Mapper interface/abstract class
  after/     # expected output state of those same file(s), filename-matched to before/
```

Conventions, mirrored from the existing cases:

- One file per role, named after the type (e.g. `before/CustomerMapper.java` ⇄ `after/CustomerMapper.java`
  ⇄ `context/CustomerMapperImpl.java`). Keep names consistent across the three dirs.
- `context/` holds supporting types (`CustomerDto`, `CustomerEntity`) plus the MapStruct-generated
  `*MapperImpl.java`. The generated impl must carry the real `@Generated(value = "org.mapstruct...")`
  annotation so the scanner recognizes it.
- Fixtures are loaded in the test with `readResource("fixtures/<testName>/<role>/<File>.java")`.

A test wires fixtures to virtual source paths via `spec.path(...)`. **The path matters**: generated
impls live under `build/generated/annotationProcessor/main/java/...` and real sources under
`src/main/java/...`, mirroring a real Gradle project. The generated impl's `after` is `null` (it gets
deleted — see below); the `@Mapper` file's `before`→`after` shows the inlined result.

### Asserting the generated impl is deleted

To assert a source file is removed by the recipe, pass `null` as the `after` content:

```java
java(
    readResource(".../context/UserMapperImpl.java"),
    (String) null,                         // expect this file to be deleted
    spec -> spec.path("build/generated/annotationProcessor/main/java/.../UserMapperImpl.java")
);
```

### Adding a new test case (TDD order)

1. Create a `fixtures/<newCase>/` directory with `context/`, `before/`, and an `after/` containing a
   `PLACEHOLDER` for each asserted file.
2. Add a `@Test` (optionally `@DocumentExample`) method following the existing ones as templates, and
   wire each fixture with the correct `spec.path(...)`.
3. Run just that test — read the `but was:` output to see what the recipe actually produces.
4. Verify that output is correct (fix the recipe if not), then paste the confirmed output into the
   `after/` files.
5. `./gradlew test` to confirm green with no regressions.

---

## Testing the recipe against a real project

The unit-test harness catches many issues, but the definitive validation is running the recipe on a
real codebase. This section documents the setup steps and common pitfalls discovered through that
experience.

### Publish a local snapshot

`publishToMavenLocal` fails without GPG keys because signing is configured for all publications.
The `nebula.release` snapshot strategy skips signing automatically, so use:

```bash
./gradlew snapshot publishNebulaPublicationToMavenLocal
```

This produces version `x.y.z-SNAPSHOT` in `~/.m2/repository/io/github/santunioni/purge-mapstruct/`.

**Do NOT use `includeBuild`** to wire the recipe into the target project. This recipe requires
Gradle 9+, but target projects often run an older wrapper version. Gradle rejects composite builds
between incompatible versions, and the resulting `LockOutOfDateException` cascade is very hard to
debug.

### Wire the recipe into the target project's build

**`settings.gradle` — no changes needed**, provided `gradlePluginPortal()` (or a Nexus mirror of it)
is already listed in `pluginManagement.repositories`. The `org.openrewrite.rewrite` plugin is
published to the Gradle Plugin Portal.

In the target project's `build.gradle`, add the following blocks. Note that projects typically
already have one or more `repositories { }` blocks; Gradle merges all of them, so it is cleanest to
add a **dedicated new block** right after `configurations.rewrite` rather than editing existing ones:

```groovy
plugins {
    // ...existing plugins...
    id 'org.openrewrite.rewrite' version '7.35.0'
}

configurations.rewrite {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

// Dedicated block — Gradle merges all repositories{} declarations.
// mavenLocal() resolves the snapshot you published with publishNebulaPublicationToMavenLocal.
// The Sonatype URL is only needed if you want to pull a published snapshot from Maven Central
// instead of (or in addition to) a local one.
repositories {
    mavenLocal()
    maven {
        url "https://central.sonatype.com/repository/maven-snapshots/"
    }
}

dependencies {
    rewrite "io.github.santunioni:purge-mapstruct:0.2.0-SNAPSHOT"
    // optional for cleanup passes:
    // rewrite "org.openrewrite.recipe:rewrite-static-analysis:2.22.0"
}

rewrite {
    activeRecipe("io.github.santunioni.recipes.PurgeMapstruct")
    throwOnParseFailures = false   // recommended for large codebases with generated files
}
```

Also redirect annotation-processor output so the generated `*Impl` files land in a directory that
OpenRewrite can parse:

```groovy
compileJava {
    options.generatedSourceOutputDirectory = file("$projectDir/src/generated/java")
}
sourceSets {
    main {
        java.srcDirs += "$projectDir/src/generated/java"
    }
}
```

Without these two settings the generated `*Impl` sources are either missing from the source set or
written into the `build/` tree where OpenRewrite may not scan them.

### Dependency locking

Many monorepos enforce strict dependency locking. Adding the `rewrite` configuration introduces new
dependencies that are not yet in the lock file. Depending on the locking plugin used, you may need
one of:

```bash
./gradlew refreshLocks                                   # custom task, if available
./gradlew resolveAndLockAll --write-locks --refresh-dependencies
./gradlew dependencies --write-locks                     # vanilla Gradle lock update
```

After updating locks, verify `gradle.lockfile` (or equivalent) no longer contains stale entries
that reference artifacts no longer resolved (this can silently remain after a failed prior run).

### Memory

`rewriteRun` parses every source file in memory. Large codebases easily exhaust the default JVM
heap. Add to `gradle.properties` in the target project:

```properties
org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=512m
```

Gradle workers (the processes that actually execute the task) respect `org.gradle.jvmargs`, unlike
`GRADLE_OPTS` which only affects the Gradle client process.

### The iteration loop

After setup, the recommended loop is:

```
1. git checkout -- src/              # revert application sources to HEAD
   rm -rf src/generated/            # clear generated sources
2. ./gradlew --stop                  # clear Gradle daemon VFS (see note below)
3. ./gradlew compileJava compileTestJava   # regenerate MapStruct *Impl files
4. ./gradlew rewriteRun
5. ./gradlew --stop                  # mandatory before recompiling (see note)
6. ./gradlew compileJava compileTestJava   # verify no compile errors
7. If errors → fix the recipe, publish new snapshot, goto 1
```

**Why `--stop` before step 6?** `rewriteRun` deletes the generated `*Impl.java` files as part of
the migration. The Gradle daemon keeps an in-memory Virtual File System (VFS) that still references
those deleted files. Without stopping the daemon first, the subsequent `compileJava` fails with
`Failed to normalize content of '...Impl.java'` even though those files are gone. Stopping the
daemon clears the VFS so the next build starts with a clean file-system view.

### Known patterns that the recipe intentionally skips

- **`@DecoratedWith` mappers** — the decorator class `implements` the mapper interface; converting
  the interface to a class would break that pattern. The recipe detects `@DecoratedWith` during the
  scan pass and leaves both the `@Mapper` interface and its generated `*Impl` unchanged.

- **Mappers where exactly one generated impl cannot be found** — the recipe logs `severe` and skips
  rather than producing broken code.

### Common recipe-induced compile errors and their causes

| Symptom after `rewriteRun` | Root cause | Recipe fix |
|---|---|---|
| `reference to Foo is ambiguous` | Two imports with the same simple name | Import conflict detection in `copyImports` drops the interface's import in favour of the impl's; interface methods are then visited to qualify the dropped type by FQN. |
| `cannot find symbol: @Context` | `@Context` appears as a *type annotation* after `final` (`final @Context List<…>`), not as a leading annotation | `transformMapperDeclMethod` unwraps `J.AnnotatedType` on parameter type expressions before stripping MapStruct annotations. |
| `X is not public in Mapper; cannot be accessed from outside package` | Interface `static` methods are implicitly `public`; the class conversion makes them package-private | `transformMapperDeclMethod` now adds an explicit `public` modifier to all interface methods that lack an access modifier. |
| `incompatible types: cannot infer type-variable(s)` | Follows from the above access error on a method reference used as a generic `Function<…>` argument | Same fix as above. |
| `interface expected here` (in a decorator class) | `@DecoratedWith` mapper was converted to a class | Handled by the skip logic described above. |
