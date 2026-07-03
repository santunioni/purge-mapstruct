package io.github.santunioni.fixtures;

@lombok.extern.java.Log
public class CustomerMapper {
    protected static final java.lang.String PERSONAL_DATA_TYPE = "PERSONAL_DATA";

    @lombok.Getter
    @lombok.Setter
    private java.lang.Long myChildField;

    @lombok.Getter
    @lombok.Setter
    private java.lang.Long myParentField;

    public io.github.santunioni.fixtures.CustomerDto toCustomerDto(io.github.santunioni.fixtures.CustomerEntity customerEntity) {
        if (customerEntity == null) {
            return null;
        }

        java.lang.String name = customerEntity.getName();
        java.lang.String email = customerEntity.getEmail();

        return new io.github.santunioni.fixtures.CustomerDto(name, email);
    }

    public io.github.santunioni.fixtures.CustomerEntity toCustomerEntity(io.github.santunioni.fixtures.CustomerDto customerDto) {
        if (customerDto == null) {
            return null;
        }

        java.lang.String name = customerDto.getName();
        java.lang.String email = customerDto.getEmail();

        io.github.santunioni.fixtures.CustomerEntity customerEntity = new io.github.santunioni.fixtures.CustomerEntity(name, email);
        if (customerEntity != null && customerEntity.getEmail() != null) {
            customerEntity.setEmail(customerEntity.getEmail().toLowerCase());
        }
        return customerEntity;
    }

    public java.lang.String getSignature(io.github.santunioni.fixtures.CustomerEntity customerEntity) {
        return customerEntity.getName() + " <" + customerEntity.getEmail() + ">";
    }

}
