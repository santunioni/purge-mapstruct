# Rewrite recipe starter

This is an openrewrite recipe that purges mapstruct from your codebase.

## Why?

I believe mapstruct is a library that hurts your codebase. Its greatest feature is allowing you to auto-transform
your value classes by matching field names. When it can't match, your field is either unmapped or you must write a
manual mapping, which is just writing java code as string and losing the compile-time safety.

When you don't map fields correctly, you get a bunch of nulls in production.

## How?

The recipe replaces your mapper declaration with its respective implementation. The output code will be bad code, I
know, but at least it will be yours to change. In this document I intend to teach you how to use this recipe
naively, replacing by the bad generated code, so you can get your hands dirty. But don't commit the code yet! Just
make sure your software builds and your tests pass. Then you rollback the changes and keep reading the document to
understand how you can improve the quality of the generated code automatically.

## Using the recipe naively

- Use the moderne CLI

## Using the recipe smartly

- Pick quality recipes from openrewrite that you like. My advices are:
    - CodeCleanup
    - Spring if you are using spring
- Run the recipe `...` to add the lombok @Builder annotation to all classes that are output of your mappers. This
  will make mapstruct use the builder classes in generated code. This is better because otherwise mapstruct will
  generate a constructor with all fields, which is hard to read because java doesn't have keyword variables.
- Run the quality recipes and commit your code
- Run the `RemoveMapstruct` recipe. Make sure your software builds and your tests pass. Commit the code.
- Run the quality recipes again and commit your code.