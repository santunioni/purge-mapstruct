package com.santunioni.fixtures;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public abstract class CustomerMapper {
    public abstract CustomerDto toCustomerDto(CustomerEntity customerEntity);

    @AfterMapping
    protected void addSignature(@MappingTarget final CustomerDto customerDto, final CustomerEntity customerEntity) {
        customerDto.setName(customerDto.getName() + " <" + customerEntity.getEmail() + ">");
    }
}
