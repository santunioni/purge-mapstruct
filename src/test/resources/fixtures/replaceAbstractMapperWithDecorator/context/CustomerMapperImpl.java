package com.santunioni.fixtures.dtoMappers;

import javax.annotation.processing.Generated;

@Generated(
        value = "org.mapstruct.ap.MappingProcessor",
        date = "2025-01-01T00:00:00Z",
        comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
public class CustomerMapperImpl extends CustomerMapper {

    @Override
    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        String name = customerEntity.getName();
        String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }

    @Override
    public CustomerEntity toCustomerEntity(CustomerDto customerDto) {
        if (customerDto == null) {
            return null;
        }

        String name = customerDto.getName();
        String email = customerDto.getEmail();

        CustomerEntity customerEntity = new CustomerEntity(name, email);
        return super.toCustomerEntity(customerDto);
    }

}
