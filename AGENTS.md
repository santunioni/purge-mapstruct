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
- The recipe under test is configured in `defaults(RecipeSpec)`: it runs `PurgeMapstruct` followed
  by `AutoFormat`, with `mapstruct`, `lombok`, and `junit-jupiter-api` on the parser classpath.

### Fixture layout

Each test reads `.java` fixtures from `src/test/resources/fixtures/<testName>/`:

```
fixtures/<testName>/
  context/   # files that must exist for parsing/linking but whose final state we don't assert
             # (DTOs, entities, and the generated *MapperImpl.java)
  before/    # input state of files we assert on
  after/     # expected output state of those files
```

A test wires fixtures to virtual source paths via `spec.path(...)`. **The path matters**: generated
impls live under `build/generated/annotationProcessor/main/java/...` and real sources under
`src/main/java/...`, mirroring a real Gradle project.

### Asserting the generated impl is deleted

To assert a source file is removed by the recipe, pass `null` as the `after` content:

```java
java(
    readResource(".../context/UserMapperImpl.java"),
    (String) null,                         // expect this file to be deleted
    spec -> spec.path("build/generated/annotationProcessor/main/java/.../UserMapperImpl.java")
);
```

### Adding a new test case

1. Create a `fixtures/<newCase>/` directory with `context/`, `before/`, `after/` as needed.
2. Add a `@Test` (optionally `@DocumentExample`) method following the existing two as templates.
3. Wire each fixture with the correct `spec.path(...)`.
4. `./gradlew test`.
