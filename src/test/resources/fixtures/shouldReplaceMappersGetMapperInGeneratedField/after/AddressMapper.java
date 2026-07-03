package io.github.santunioni.fixtures;

public class AddressMapper {
    public io.github.santunioni.fixtures.CustomerDto toCustomerDto(io.github.santunioni.fixtures.CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        return new io.github.santunioni.fixtures.CustomerDto(name, email);
    }
}
