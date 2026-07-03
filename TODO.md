# Open Issues

## A.1 — `@DecoratedWith` mapper is not skipped

### Problem

MapStruct supports the `@DecoratedWith` pattern: a mapper interface carries
`@DecoratedWith(SomeDecorator.class)`, and a hand-written decorator class
`implements` that interface to add extra behaviour around the generated code.

The recipe is supposed to detect this during the **scan pass** and leave both
the `@Mapper` interface and its generated `*Impl` untouched (AGENTS.md, "Known
patterns that the recipe intentionally skips"). However, at least one real
codebase shows that a mapper annotated with both `@Mapper` and
`@DecoratedWith(...)` is **not skipped**: the recipe converts the interface to a
concrete class. This breaks the decorator because the decorator class has
`implements SomeMapper` — which now refers to a class, not an interface — and
the compiler emits `interface expected here`.

### Root-cause hypothesis

`ImplementationScanner` or `Functions.isMapperDeclaration` does not consult the
`@DecoratedWith` annotation when deciding whether to process a mapper. The skip
logic needs to also inspect the annotation list of the `@Mapper` class for
`@DecoratedWith` and, when found, record the mapper's FQN in the accumulator as
"do not process".

### Acceptance criterion

Given a `@Mapper` interface that also carries `@DecoratedWith(Decorator.class)`,
the recipe must leave the interface **and** the generated `*Impl` completely
unchanged. A regression test should cover this case.

---

## A.3 — `@Context` type annotation is not stripped from method parameters

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

### Root-cause

In `transformMapperDeclMethod`, the parameter transformation filters
`varDecl.getLeadingAnnotations()` but does not inspect whether
`varDecl.getTypeExpression()` is a `J.AnnotatedType`. When it is, the
annotation inside needs to be checked and removed if it is a MapStruct
annotation, and the `J.AnnotatedType` must be replaced with its unwrapped inner
type.

### Acceptance criterion

A mapper with a default/static method that has a `final @Context List<Foo>`
parameter must have the `@Context` annotation stripped and the parameter
reduced to `final List<Foo>` in the merged output. A regression test should
cover this exact parameter form.

---

## A.4 — Lombok-generated inner builder class imported by simple inner-class name

### Problem

When the generated `*Impl` references a **Lombok-generated nested builder
class** by its inner-class import form — e.g.
`import some.pkg.FooDTO.FooDTOBuilder;` — the recipe copies that import verbatim
into the merged mapper class. At compile time this import resolves to a
Lombok-synthesised static nested class inside `FooDTO`. In some configurations
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
