package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public interface OrderMapper {
    OrderDto toOrderDto(String id);

    String STATUS = "PENDING";
    final String DEFAULT_STATUS = "PENDING";
    static final String DEFAULT_SITUATION = "PENDING";
}
