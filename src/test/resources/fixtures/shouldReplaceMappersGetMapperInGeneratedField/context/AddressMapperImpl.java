package com.santunioni.fixtures;

import javax.annotation.processing.Generated;

@Generated(
        value = "org.mapstruct.ap.MappingProcessor",
        date = "2025-01-01T00:00:00Z",
        comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
public class AddressMapperImpl implements AddressMapper {

    @Override
    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        String name = customerEntity.getName();
        String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }
}
