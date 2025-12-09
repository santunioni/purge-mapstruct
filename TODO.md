# Fixes

## Implementation

Copy fields as is. Don't do any accessor transformations. It fails spring when I
add final to fields.
![bug-field-access-transformation.png](docs/bug-field-access-transformation.png)

Rename implementation classes when they are passed as class parameters into annotations
![bug-mapper-as-annotation-parameter.png](docs/bug-mapper-as-annotation-parameter.png)

## Declaration

Default methods and fields copied from interfaces should be explicitly set as public. Non-default should keep their
visibility. They should also not be made static
![bug-default-methods-not-public.png](docs/bug-default-methods-not-public.png)

# Features

Add another recipe that inserts a lombok @Builder decorator into all classes that are target of a mapper
