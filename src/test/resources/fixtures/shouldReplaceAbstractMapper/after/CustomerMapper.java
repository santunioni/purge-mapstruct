package io.github.santunioni.fixtures;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

@Log
public class CustomerMapper {
    protected static final java.lang.String PERSONAL_DATA_TYPE = "PERSONAL_DATA";

    @Getter
    @Setter
    private java.lang.Long myChildField;

    @Getter
    @Setter
    private java.lang.Long myParentField;

    public CustomerDto toCustomerDto(CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        return new CustomerDto(name, email);
    }

    public CustomerEntity toCustomerEntity(CustomerDto customerDto) {
        if (customerDto == null) {
            return null;
        }

        java.lang.String name = customerDto.getName();
        java.lang.String email = customerDto.getEmail();

        CustomerEntity customerEntity = new CustomerEntity(name, email);
        if (customerEntity != null && customerEntity.getEmail() != null) {
            customerEntity.setEmail(customerEntity.getEmail().toLowerCase());
        }
        return customerEntity;
    }

    public java.lang.String getSignature(CustomerEntity customerEntity) {
        return customerEntity.getName() + " <" + customerEntity.getEmail() + ">";
    }

}
