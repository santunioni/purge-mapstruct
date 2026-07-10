package io.github.santunioni.fixtures;

import org.mapstruct.DecoratedWith;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
@DecoratedWith(FooMapperDecorator.class)
public interface FooMapper {
    TargetDto toDto(SourceEntity entity);

    TargetDto toDecoratedDto(SourceEntity entity);
}
