package com.santunioni.fixtures;

import org.mapstruct.Mapper;

@Mapper
public interface AddressMapper {
    CustomerDto toCustomerDto(CustomerEntity customerEntity);
}
