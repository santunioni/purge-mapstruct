# Open Issues

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
