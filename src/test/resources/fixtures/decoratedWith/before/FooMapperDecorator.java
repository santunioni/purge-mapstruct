package io.github.santunioni.fixtures;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class FooMapperDecorator implements FooMapper {

    @Autowired
    @Qualifier("delegate")
    private FooMapper delegate;

    @Override
    public TargetDto toDecoratedDto(SourceEntity entity) {
        TargetDto dto = delegate.toDecoratedDto(entity);
        dto.setName(dto.getName().toUpperCase());
        return dto;
    }
}
