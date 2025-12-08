package com.santunioni.fixtures.dtoMappers;

import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-01-01T00:00:00Z",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
public class SimpleDtoMapperImpl implements SimpleDtoMapper {

    @Override
    public SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn) {
        if (simpleDtoIn == null) {
            return null;
        }

        Long id = simpleDtoIn.getId();
        String name = simpleDtoIn.getName();

        return new SimpleDtoOut(id, name);
    }

    @Override
    public SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut) {
        if (simpleDtoOut == null) {
            return null;
        }

        Long id = simpleDtoOut.getId();
        String name = simpleDtoOut.getName();

        return new SimpleDtoIn(id, name);
    }
}
