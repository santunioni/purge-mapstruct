package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;


public class ProductMapper {

    public ProductMapper() {
    }
    public ProductDto toProductDto(String name) {
        return new ProductDto(name);
    }
}
