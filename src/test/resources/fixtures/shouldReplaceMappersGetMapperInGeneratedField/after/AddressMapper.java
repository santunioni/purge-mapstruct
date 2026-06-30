package com.santunioni.fixtures;

public class AddressMapper {
    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        String name = customerEntity.getName();
        String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }
}
