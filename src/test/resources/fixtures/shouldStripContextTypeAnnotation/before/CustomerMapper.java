package io.github.santunioni.fixtures;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper
public interface CustomerMapper {

    @Mapping(target = "email", source = "name")
    CustomerDto toCustomerDto(CustomerEntity customerEntity, @Context List<String> tags);

    @Named("describe")
    default String describe(final CustomerEntity customerEntity, final @Context List<String> tags) {
        return customerEntity.getName() + " " + tags.size();
    }
}
