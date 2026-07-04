package io.github.santunioni.fixtures;

public class CustomerMapper {
    public static final io.github.santunioni.fixtures.CustomerMapper INSTANCE = new io.github.santunioni.fixtures.CustomerMapper();

    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }
}
