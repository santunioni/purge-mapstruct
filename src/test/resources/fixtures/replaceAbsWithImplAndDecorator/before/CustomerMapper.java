package com.santunioni.fixtures.dtoMappers;

import com.santunioni.fixtures.decorators.Decorator;
import org.mapstruct.Mapper;


@Decorator
@Mapper
public abstract class CustomerMapper {
    protected static final String PERSONAL_DATA_TYPE = "PERSONAL_DATA";

    public abstract CustomerEntity toCustomerEntity(CustomerDto customerDto);

    public abstract CustomerDto toCustomerDto(CustomerEntity customerEntity);

    public String getSignature(CustomerEntity customerEntity) {
        return customerEntity.getName() + " <" + customerEntity.getEmail() + ">";
    }

}
