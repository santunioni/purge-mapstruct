package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;


public class CustomerMapper {
    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        String name = customerEntity.getName();
        String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }

    public CustomerEntity toCustomerEntity(CustomerDto customerDto) {
        if (customerDto == null) {
            return null;
        }

        String name = customerDto.getName();
        String email = customerDto.getEmail();

        CustomerEntity customerEntity = new CustomerEntity(name, email);
        if (customerEntity != null && customerEntity.getEmail() != null) {
            customerEntity.setEmail(customerEntity.getEmail().toLowerCase());
        }
        return customerEntity;
    }

}
