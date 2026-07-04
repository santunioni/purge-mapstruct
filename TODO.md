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
