package io.github.santunioni.fixtures;

import java.util.List;

import javax.annotation.processing.Generated;

@Generated(
        value = "org.mapstruct.ap.MappingProcessor",
        date = "2025-01-01T00:00:00Z",
        comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
public class CustomerMapperImpl implements CustomerMapper {

    @Override
    public CustomerDto toCustomerDto(CustomerEntity customerEntity, List<String> tags) {
        if (customerEntity == null) {
            return null;
        }

        CustomerDto customerDto = new CustomerDto();

        customerDto.setEmail(customerEntity.getName());

        return customerDto;
    }
}
