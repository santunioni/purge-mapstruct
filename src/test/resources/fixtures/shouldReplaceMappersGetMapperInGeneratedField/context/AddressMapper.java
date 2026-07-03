package io.github.santunioni.fixtures;

import org.mapstruct.Mapper;

@Mapper
public interface AddressMapper {
    CustomerDto toCustomerDto(CustomerEntity customerEntity);
}
