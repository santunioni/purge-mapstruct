# Open Issues

## `@DecoratedWith` mapper is not skipped

### Problem

MapStruct supports the `@DecoratedWith` pattern: a mapper interface carries
`@DecoratedWith(SomeDecorator.class)`, and a hand-written decorator class
`implements` that interface to add extra behaviour around the generated code.
We aren't inlining those structures yet, but we should. We should figure out
how.

### Acceptance criterion

These are stripped from the codebase:
- The @DecoratedWith annotation
- The decorator class, `SomeDecorator.class`, when its purpose was to be
  referenced by the mapper interface.
  - Generated code already covers the functionality? So delete the `SomeDecorator.class`.
  - Generated code doesn't cover the functionality and user as using `SomeDecorator.class` directly? Then refactor the
    class to the minimal thing that doesn't bother about mapstruct.

### What MapStruct actually generates for `@DecoratedWith`

Given:

```java
@Mapper(componentModel = "spring")
@DecoratedWith(FooMapperDecorator.class)
public interface FooMapper { /* mapping methods */ }

public abstract class FooMapperDecorator implements FooMapper {
    @Autowired @Qualifier("delegate") private FooMapper delegate;
    // overrides *some* methods, each doing: delegate.x(...); then custom logic
}
```

MapStruct emits **two** generated classes (not one). Together with the interface
and the hand-written decorator, this pattern is a **four-body** structure the
recipe must fold into a single class — and all four bodies get consumed/deleted:

1. **`FooMapper`** (the `@Mapper @DecoratedWith(...)` interface) — the declared
   API. Contributes any `default`/`static` methods and interface constants, and
   is the name/source path the merged class is written back onto.
2. **`FooMapperDecorator`** — the hand-written class. MapStruct **requires the
   decorator to be a subtype of the mapper**, so it always `implements FooMapper`
   (or `extends` an abstract class that does) — meaning **three** types implement
   `FooMapper` at compile time (decorator, delegate, primary). But the decorator
   is **not** `@Generated`, so `isMapperImplementation` does **not** detect it and
   the scanner never links it as an implementer — it is discovered instead via the
   `@DecoratedWith(FooMapperDecorator.class)` argument on the interface. Usually
   `abstract` (so it can decorate only some methods); it carries the only custom
   behaviour: each **overridden** method calls `delegate.x(...)` then
   post-processes. **Its sole purpose is to be wired in by MapStruct via
   `@DecoratedWith`, so once its logic is folded into the merged class it is
   deleted** (the "generated code covers the functionality → delete
   `SomeDecorator.class`" branch of the acceptance criterion).
3. **`FooMapperImpl_`** (trailing underscore) — the *delegate*. Annotated
   `@Component @Qualifier("delegate")`, `implements FooMapper`. Holds the pure,
   field-matched mapping logic plus all the private/protected sub-mapping and
   map-loop helpers. This is the real generated code. Deleted after its bodies
   are copied in.
4. **`FooMapperImpl`** — annotated `@Component @Primary`,
   `extends FooMapperDecorator`. Has its own `@Autowired @Qualifier("delegate")`
   field and, for every method the decorator does **not** override, a trivial
   `return delegate.x(...)` pass-through. Adds no logic of its own → deleted.

At runtime Spring injects the `@Primary` `FooMapperImpl`, so **the two method
kinds take different paths** — and the recipe must reproduce each:

- **Overridden method** (custom behaviour lives in the decorator). Call resolves
  to `FooMapperDecorator.x(...)`, which does `delegate.x(...)` (pure mapping in
  `FooMapperImpl_`) and then its post-processing. Example
  (`mapToHouseScheduleAvailabilityDTO`): decorator runs the generated mapping,
  then rewrites the top-level weekday keys from numbers to names.
- **Non-overridden method** (no custom behaviour). Call resolves to
  `FooMapperImpl.x(...)`, whose body is just `return delegate.x(...)` — i.e. the
  pure `FooMapperImpl_` mapping, unmodified. Example
  (`mapToHouseScheduleDTO`): straight field-matched mapping, no decoration.

**Why the recipe leaves it untouched today:** three types implement `FooMapper`,
but only the two `@Generated` ones (`FooMapperImpl` and `FooMapperImpl_`) pass
`isMapperImplementation`, so `MappersGathererScanner` links *both* of them to the
same super FQN `FooMapper` (the hand-written decorator is never linked).
`InlineMapstruct.getImplementer` requires exactly one implementer, finds two, and
skips — so all four bodies survive.

### Target output (behaviour-preserving)

**Two concrete classes, keeping the decorator/delegate split** (the interface is
deleted). This is preferred over folding everything into one class: it preserves
the exact call structure MapStruct produced, so behaviour is trivially the same —
we only rename types, retype the injection, and strip the MapStruct/`@Primary`
wiring. Because `FooMapperImpl extends FooMapperDecorator`, the merge is a natural
one:

1. **`FooMapper`** (concrete `@Component` class, replaces the interface) =
   **`FooMapperDecorator` merged with `FooMapperImpl`**. The decorator is already
   the abstract base; the primary impl is its concrete subclass. Collapse the
   subclass into the base:
   - Keep the decorator's **overridden** methods verbatim (custom logic + their
     private helpers `convertWeekDaysToNames`, `numberToDayName`, …).
   - Pull down `FooMapperImpl`'s **non-overridden** pass-through methods
     (`return delegate.x(...)`) so the class is concrete and complete.
   - The class no longer `extends`/`implements` anything and is named `FooMapper`.
2. **`FooMapperDelegate`** (concrete `@Component` class, renamed from
   `FooMapperImpl_`) = the pure generated mapping body, unchanged, **but it no
   longer implements `FooMapper`** (the interface is gone). It stands alone as a
   plain mapper class.
3. **Wiring by concrete type:** `FooMapper` depends on `FooMapperDelegate`
   **directly by type** — the `@Autowired @Qualifier("delegate")` `FooMapper delegate`
   field becomes a `FooMapperDelegate delegate` (constructor-injected, no
   `@Qualifier` needed since there's a single bean of that type). Every
   `delegate.x(...)` call keeps working unchanged, now resolved against the
   concrete delegate class
4. **`FooMapperDelegate`** should be a nested static class of **`FooMapper`**
   (the class replacing the interface). 
5. Both final **`FooMapperDelegate`** and **`FooMapper`** classes
   should be post-processed by the recipes that come after transformation.  

Everything MapStruct-specific is stripped: `@DecoratedWith`, `@Qualifier`,
`@Primary`, and all `org.mapstruct` imports/annotations are gone; references to
`FooMapperImpl` and `FooMapperImpl_`/decorator elsewhere rewrite to `FooMapper` /
`FooMapperDelegate` respectively.

**Why this is behaviour-preserving by construction:** we do **not** inline the
delegate into the decorator, so the delegate's internal cross-method calls (an
outer map method calling a per-entry mapping method) still resolve to the
delegate's *own* undecorated methods — exactly as at runtime today. The
decorator's post-processing still wraps only at the call sites where it explicitly
invokes `delegate.x(...)`. No method-body rewriting, no `xDelegate` renaming, no
risk of routing nested calls through decorated methods.

### Plan (test-first, per AGENTS.md conventions)

Keep the core merge (`InlineMapstruct`) focused on the plain single-impl case;
add decorated-mapper handling as extra scan-pass roles + a dedicated merge
visitor + small composable post visitors. Work strictly TDD: write the fixture,
read the `but was:` block, then implement.

Naming rule the recipe applies: strip the `Impl` / `Impl_` suffixes and drop the
interface, so the decorator+primary become `FooMapper` and the delegate becomes
`FooMapperDelegate` (i.e. `FooMapperImpl_` → interface-name + `Delegate`).

1. **Fixture first (`fixtures/decoratedWith/`).** Create `context/` with the DTOs,
   the hand-written `FooMapperDecorator.java`, and *both* generated impls
   `FooMapperImpl.java` (`@Primary`, `extends FooMapperDecorator`) and
   `FooMapperImpl_.java` (`@Qualifier("delegate")`, `implements FooMapper`) — both
   carrying `@Generated(value = "org.mapstruct...")`. `before/` holds the
   `@DecoratedWith` interface `FooMapper.java` and the decorator; `after/` starts
   as `PLACEHOLDER`. Expected end state to assert:
   - `FooMapper.java` (interface) → **deleted** (`null`); its source path is reused
     for the merged concrete class.
   - `FooMapperDecorator.java` → **deleted** (`null`). Its body is merged into the
     concrete `FooMapper` class, which is written onto the **interface's** source
     path + id (consistent with how the plain merge writes onto the mapper
     declaration's path).
   - `FooMapperImpl.java` → **deleted** (`null`).
   - `FooMapperImpl_.java` → becomes `FooMapperDelegate.java` (renamed, no longer
     implements the interface).
   Wire generated impls under `build/generated/annotationProcessor/main/java/...`,
   add the `@Test`, run it to capture ground truth.

2. **Detection helpers (`inlineMapstruct/Functions.kt`).** Add:
   - `isDecoratedMapperDeclaration` — a mapper declaration carrying
     `org.mapstruct.DecoratedWith`; read its `Decorator.class` argument FQN.
   - `isMapperDelegate` — a generated impl whose Spring `@Qualifier` value is
     `"delegate"` (the `FooMapperImpl_`), vs. the `@Primary` primary `FooMapperImpl`.

3. **Scan pass (`MappersGathererScanner` + `MapstructRefsWriter`).** For a
   decorated mapper the super has two `@Generated` implementers plus a
   `@DecoratedWith`-named decorator. Record the roles instead of a single linking:
   `super → delegateImpl` (`FooMapperImpl_`), `super → primaryImpl`
   (`FooMapperImpl`), `super → decoratorFqn`. Extend `MapstructRefs` + the
   writer/reader interfaces with minimal accessors (`getDelegateImplementer`,
   `getPrimaryImplementer`, `getDecoratorFor`), keeping the write/read segregation.

4. **Merge visitor for the decorator side (`recipes/InlineDecoratedMapper.kt`,
   `internal JavaVisitor`, reader-backed).** When visiting the **interface** file
   (the host for the result, matching the plain merge which writes onto the mapper
   declaration's path):
   - produce the concrete `FooMapper` = decorator body (overridden methods +
     private helpers) **plus** the non-overridden pass-through methods pulled down
     from `FooMapperImpl` (so the class is complete and concrete), plus the
     interface's own `default`/`static` methods and constants (as the plain merge
     already does);
   - drop `extends FooMapperDecorator` / `implements FooMapper`, name it
     `FooMapper`, write onto the interface's source path + id;
   - **retype the delegate field**: `@Autowired @Qualifier("delegate") FooMapper
     delegate` → constructor-injected `FooMapperDelegate delegate` (no
     `@Qualifier`); leave every `delegate.x(...)` call site untouched — it now
     binds to `FooMapperDelegate` by type.
   Keep this out of `InlineMapstruct` (which stays the plain single-impl merge),
   per the "small, composable visitors" convention.

5. **Rename the delegate (`recipes/RenameMapperDelegate.kt` or extend an existing
   rename visitor).** Rename `FooMapperImpl_` → `FooMapperDelegate` (class name +
   source path), keep it a `@Component`, and **remove `implements FooMapper`** and
   the `@Qualifier("delegate")` marker. Its bodies are unchanged.

6. **Deletion + reference rewrite (post visitors).**
   - `DeleteMapperImplementations` — return `null` for the **primary**
     `FooMapperImpl` (its logic was pulled into the merged class) and for the
     **decorator** `FooMapperDecorator` (its body was merged onto the interface
     path). Do **not** delete the delegate — it survives as `FooMapperDelegate`.
     The interface file itself is not "deleted" so much as **overwritten** by the
     merged concrete class (same source path + id).
   - Decorator disposal (per acceptance criterion): the "generated code covers the
     functionality" case is the one wired here — the decorator's logic moves into
     the merged `FooMapper` and the decorator file is deleted. Only if the
     decorator is referenced directly by user code beyond MapStruct wiring would we
     instead reduce it to a minimal plain class — defer that branch behind a
     follow-up until a real fixture needs it.
   - `RewriteImplReferences` — rewrite `FooMapperImpl` / `FooMapperImpl_` /
     decorator-type references (fields, params, `instanceof`, `.class`, imports) to
     `FooMapper` / `FooMapperDelegate` as appropriate.
   - `StripMapstructAnnotations` — remove `@DecoratedWith` (on the interface/merged
     class) and `@Qualifier` alongside the other `org.mapstruct` annotations.

7. **Green the fixture.** Read the `but was:` output, verify it matches the
   Target output (two concrete classes; `FooMapper` with decorator logic + pulled
   pass-throughs, constructor-injecting `FooMapperDelegate`; `FooMapperDelegate`
   standalone, not implementing the interface; no mapstruct imports/annotations;
   references rewritten), paste into `after/`, then `./gradlew test` for
   regressions.

8. **Validate against a real project** using the AGENTS.md loop (publish snapshot
   → `rewriteRun` → `--stop` → `compileJava compileTestJava test`). The
   backoffice-bff `HouseAvailabilityMapper` (interface + `HouseAvailabilityMapperDecorator`
   + `HouseAvailabilityMapperImpl`/`_`) is the representative real target: expect
   `HouseAvailabilityMapper` (merged) + `HouseAvailabilityMapperDelegate` (former
   `Impl_`) out the other side.

### Open questions / risks

- **Where to host the merged class — decided:** write the merged `FooMapper` onto
  the **interface's** source path + id and **delete the decorator file**. This
  matches the rest of the recipe (the plain merge already writes onto the mapper
  declaration's path) and keeps the public type on its original path, which
  callers/imports expect. Do not host it on the decorator's path.
- **Single-bean uniqueness.** After removing `@Qualifier("delegate")`, wiring
  `FooMapperDelegate` by type must be unambiguous — it is, since the interface is
  gone and only one bean of that concrete type exists. Confirm no other bean
  implements/extends it.
- **Decorator referenced directly by user code** (second acceptance branch). Scope
  the first PR to the "generated code covers it" case; handle "reduce to minimal
  class" only when a real fixture demands it.
- **Redundant re-sets** the decorator performs that the delegate already did
  (e.g. `setHouseId`) are harmless no-ops — keep them byte-for-byte; do not
  "optimise" them away.

---

## `@Context` type annotation is not stripped from method parameters

### Problem

MapStruct's `@Context` annotation can appear as a **type annotation** on a
method parameter — placed *after* `final` and *before* the type itself:

```java
public void afterMap(@MappingTarget TargetType target, final @Context List<ContextType> ctx) { ... }
```

The recipe strips MapStruct annotations from `J.VariableDeclarations.leadingAnnotations`,
which covers annotations written *before* the variable declaration (e.g.
`@MappingTarget TargetType t`). But when `@Context` is placed in the
**type-annotation position** (between `final` and the type), it is represented
in the OpenRewrite LST as a `J.AnnotatedType` node wrapping the actual type —
not as a leading annotation on the variable declaration. The current stripping
logic does not unwrap `J.AnnotatedType`, so `@Context` survives the merge and
causes a compile error once MapStruct is removed from the classpath.

### Acceptance criterion

The final code doesn't possess any `@Context` annotations, and the behavior is the behavior of using
the generated mapper is the same as if one were still using MapStruct.

---

## Lombok-generated inner builder class imported by a simple inner-class name

### Problem

When the generated `*Impl` references a **Lombok-generated nested builder
class** by its inner-class import form — e.g.
`import some.pkg.FooDTO.FooDTOBuilder;` — the recipe copies that import verbatim
into the merged mapper class. At compile time this import resolves to a
Lombok-synthesized static nested class inside `FooDTO`. In some configurations
(Lombok version, `lombok-mapstruct-binding` ordering) the import causes a
`cannot find symbol` error because the Lombok annotation processor has not yet
generated the nested type when the import is resolved.

Observed error form:
```
import some.pkg.FooDTO.FooDTOBuilder;
                      ^
  symbol:   class FooDTOBuilder
  location: class FooDTO
```

### Root-cause hypothesis

The `*Impl` was compiled (and MapStruct resolved all symbols) in a prior
`compileJava` run where Lombok already processed `FooDTO`. When the recipe
inlines the `*Impl` source into the mapper class, that inlined source now
depends on a Lombok-generated inner class. Whether this succeeds depends on
annotation-processor ordering. The recipe may need to strip inner-class imports
whose outer class is annotated with `@Builder` (or similar Lombok annotations)
and replace all usages with the correct builder accessor (`FooDTO.builder()`)
rather than an explicit inner-class reference — or alternatively ensure the
import is dropped and the variable type is changed to `var` / the outer type.

### Acceptance criterion

A mapper whose generated impl imports a Lombok `@Builder` inner class by its
nested import must compile cleanly after the recipe runs, without requiring
manual import cleanup.
