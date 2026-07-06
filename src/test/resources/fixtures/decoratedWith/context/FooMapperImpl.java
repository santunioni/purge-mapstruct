package io.github.santunioni.fixtures;

import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Generated(
        value = "org.mapstruct.ap.MappingProcessor",
        date = "2025-01-01T00:00:00Z",
        comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
@Component
@Primary
public class FooMapperImpl extends FooMapperDecorator {

    @Autowired
    @Qualifier("delegate")
    private FooMapper delegate;

    @Override
    public TargetDto toDto(SourceEntity entity) {
        return delegate.toDto(entity);
    }
}
