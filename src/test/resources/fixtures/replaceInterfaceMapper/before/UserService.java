package com.santunioni.fixtures.services;

import com.santunioni.fixtures.dtoMappers.SimpleDtoIn;
import com.santunioni.fixtures.dtoMappers.SimpleDtoMapperImpl;
import com.santunioni.fixtures.dtoMappers.SimpleDtoOut;

public class SimpleService {
    private final UserMapperImpl mapper = new UserMapperImpl();

    public UserDto process(UserEntity input) {
        return mapper.toUserDto(input);
    }
}
