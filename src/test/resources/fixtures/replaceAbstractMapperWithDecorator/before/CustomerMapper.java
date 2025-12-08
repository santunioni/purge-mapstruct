package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public abstract class CustomerMapper {
    public abstract CustomerEntity toCustomerEntity(CustomerDto customerDto);

    public abstract CustomerDto toCustomerDto(CustomerEntity customerEntity);
}
