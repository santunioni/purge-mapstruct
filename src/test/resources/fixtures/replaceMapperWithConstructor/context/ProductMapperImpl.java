package com.santunioni.fixtures.dtoMappers;

import javax.annotation.processing.Generated;

@Generated(value = "org.mapstruct.ap.MappingProcessor", date = "2025-01-01T00:00:00Z", comments = "version: 1.5.5" +
        ".Final, compiler: javac, environment: Java 17")
public class ProductMapperImpl implements ProductMapper {

    public ProductMapperImpl() {
    }

    @Override
    public ProductDto toProductDto(String name) {
        return new ProductDto(name);
    }

}
