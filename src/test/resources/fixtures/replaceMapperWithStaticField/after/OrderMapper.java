package com.santunioni.fixtures.dtoMappers;


public class OrderMapper {

    public static final String STATUS = "PENDING";

    public static final String DEFAULT_STATUS = "PENDING";

    public static final String DEFAULT_SITUATION = "PENDING";

    public OrderDto toOrderDto(String id) {
        return new OrderDto(id);
    }

}
