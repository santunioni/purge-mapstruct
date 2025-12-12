# Rewrite recipe starter

This is a openrewrite recipe that purges mapstruct from your codebase by replacing your mapper declarations with their
respective implementations.

## Why?

I believe mapstruct is a library that hurts your codebase. Its greatest feature is allowing you to auto-transform
your value classes by matching field names. When it can't match, your field is either unmapped or you must write a
manual mapping, which is just write java code as string and losing the compile-time safety.
