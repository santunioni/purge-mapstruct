package io.github.santunioni.fixtures;

public class CustomerMapper {
    public io.github.santunioni.fixtures.CustomerDto toCustomerDto(io.github.santunioni.fixtures.CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        io.github.santunioni.fixtures.CustomerDto customerDto = new io.github.santunioni.fixtures.CustomerDto(name, email);

        addSignature(customerDto, customerEntity);

        return customerDto;
    }

    protected void addSignature(final io.github.santunioni.fixtures.CustomerDto customerDto, final io.github.santunioni.fixtures.CustomerEntity customerEntity) {
        customerDto.setName(customerDto.getName() + " <" + customerEntity.getEmail() + ">");
    }

}
