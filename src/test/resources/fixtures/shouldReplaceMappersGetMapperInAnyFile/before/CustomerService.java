package io.github.santunioni.fixtures;

import org.mapstruct.factory.Mappers;

public class CustomerService {
    private final CustomerMapper mapper = Mappers.getMapper(CustomerMapper.class);

    public CustomerDto convert(CustomerEntity entity) {
        return mapper.toCustomerDto(entity);
    }
}
