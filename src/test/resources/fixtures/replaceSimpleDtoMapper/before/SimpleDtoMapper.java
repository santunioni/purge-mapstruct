package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public interface SimpleDtoMapper {
    SimpleDtoOut toSimpleDtoOut(SimpleDtoIn simpleDtoIn);
    SimpleDtoIn toSimpleDtoIn(SimpleDtoOut simpleDtoOut);
}
