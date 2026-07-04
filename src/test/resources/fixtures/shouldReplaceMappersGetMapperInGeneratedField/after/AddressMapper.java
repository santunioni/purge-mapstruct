package io.github.santunioni.fixtures;

public class AddressMapper {
    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }
}
