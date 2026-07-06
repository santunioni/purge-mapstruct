package io.github.santunioni.fixtures;

import java.util.List;

public class CustomerMapper {
    public CustomerDto toCustomerDto(CustomerEntity customerEntity, List<String> tags) {
        if (customerEntity == null) {
            return null;
        }

        CustomerDto customerDto = new CustomerDto();

        customerDto.setEmail(customerEntity.getName());

        return customerDto;
    }

    public String describe(final CustomerEntity customerEntity, final List<String> tags) {
        return customerEntity.getName() + " " + tags.size();
    }
}
