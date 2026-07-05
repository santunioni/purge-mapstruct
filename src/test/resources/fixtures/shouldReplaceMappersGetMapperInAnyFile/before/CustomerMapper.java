package io.github.santunioni.fixtures;

import org.mapstruct.Mapper;

@Mapper
public interface CustomerMapper {
    CustomerDto toCustomerDto(CustomerEntity customerEntity);
}
