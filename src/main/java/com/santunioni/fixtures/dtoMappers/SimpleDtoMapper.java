package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public interface SimpleDtoMapper {
    // Map from SimpleDtoIn to SimpleDtoOut and vice-versa

    SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn);

    SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut);
}
