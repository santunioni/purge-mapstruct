package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;


public class SimpleDtoMapper {
    public SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn) {
        if (simpleDtoIn == null) {
            return null;
        }

        Long id = simpleDtoIn.getId();
        String name = simpleDtoIn.getName();

        return new SimpleDtoOut(id, name);
    }
    public SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut) {
        if (simpleDtoOut == null) {
            return null;
        }

        Long id = simpleDtoOut.getId();
        String name = simpleDtoOut.getName();

        return new SimpleDtoIn(id, name);
    }
}
