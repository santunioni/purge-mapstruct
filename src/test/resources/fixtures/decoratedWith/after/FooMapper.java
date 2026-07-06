package io.github.santunioni.fixtures;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FooMapper {

    @Autowired
    private FooMapperDelegate delegate;

    public TargetDto toDecoratedDto(SourceEntity entity) {
        TargetDto dto = delegate.toDecoratedDto(entity);
        dto.setName(dto.getName().toUpperCase());
        return dto;
    }

    public TargetDto toDto(SourceEntity entity) {
        return delegate.toDto(entity);
    }

    @Component
    public static class FooMapperDelegate {
        public TargetDto toDto(SourceEntity entity) {
            if (entity == null) {
                return null;
            }
            TargetDto targetDto = new TargetDto();
            targetDto.setName(entity.getName());
            return targetDto;
        }

        public TargetDto toDecoratedDto(SourceEntity entity) {
            if (entity == null) {
                return null;
            }
            TargetDto targetDto = new TargetDto();
            targetDto.setName(entity.getName());
            return targetDto;
        }
    }
}
