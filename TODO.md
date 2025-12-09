# Fixes

## Implementation

Rename implementation classes when they are passed as class parameters into annotations
![bug-mapper-as-annotation-parameter.png](docs/bug-mapper-as-annotation-parameter.png)

## Generation

Remove AfterMapping and MappingTarget from the generated method
![remove-decorators.png](docs/remove-decorators.png)

# Features

Add another recipe that inserts a lombok @Builder decorator into all classes that are target of a mapper
