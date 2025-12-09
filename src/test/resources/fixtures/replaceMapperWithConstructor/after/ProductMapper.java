package com.santunioni.fixtures.dtoMappers;


public class ProductMapper {

    public ProductMapper() {
    }

    public ProductDto toProductDto(String name) {
        return new ProductDto(name);
    }

}
