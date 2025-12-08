package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public interface ProductMapper {
    ProductDto toProductDto(String name);
}
