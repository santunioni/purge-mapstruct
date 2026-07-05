package io.github.santunioni.fixtures;

public class CustomerService {
    private final CustomerMapper mapper = new CustomerMapper();

    public CustomerDto convert(CustomerEntity entity) {
        return mapper.toCustomerDto(entity);
    }
}
