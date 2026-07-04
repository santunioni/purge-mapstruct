package io.github.santunioni.fixtures;

public class CustomerMapper {
    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        CustomerDto customerDto = new CustomerDto(name, email);

        addSignature(customerDto, customerEntity);

        return customerDto;
    }

    protected void addSignature(final CustomerDto customerDto, final CustomerEntity customerEntity) {
        customerDto.setName(customerDto.getName() + " <" + customerEntity.getEmail() + ">");
    }

}
