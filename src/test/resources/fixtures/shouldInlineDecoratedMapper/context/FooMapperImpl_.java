package io.github.santunioni.fixtures;

import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Generated(
        value = "org.mapstruct.ap.MappingProcessor",
        date = "2025-01-01T00:00:00Z",
        comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
@Component
@Qualifier("delegate")
public class FooMapperImpl_ implements FooMapper {

    @Override
    public TargetDto toDto(SourceEntity entity) {
        if (entity == null) {
            return null;
        }
        TargetDto targetDto = new TargetDto();
        targetDto.setName(entity.getName());
        return targetDto;
    }

    @Override
    public TargetDto toDecoratedDto(SourceEntity entity) {
        if (entity == null) {
            return null;
        }
        TargetDto targetDto = new TargetDto();
        targetDto.setName(entity.getName());
        return targetDto;
    }
}
