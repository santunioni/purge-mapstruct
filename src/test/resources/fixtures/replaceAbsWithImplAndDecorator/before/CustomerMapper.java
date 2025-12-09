package com.santunioni.fixtures;

import lombok.extern.java.Log;
import org.mapstruct.Mapper;


@Log
@Mapper
public abstract class CustomerMapper {
    protected static final String PERSONAL_DATA_TYPE = "PERSONAL_DATA";

    public abstract CustomerEntity toCustomerEntity(CustomerDto customerDto);

    public abstract CustomerDto toCustomerDto(CustomerEntity customerEntity);

    public String getSignature(CustomerEntity customerEntity) {
        return customerEntity.getName() + " <" + customerEntity.getEmail() + ">";
    }

}
