package com.santunioni.fixtures;

import com.santunioni.fixtures.dtoMappers.SimpleDtoIn;
import com.santunioni.fixtures.dtoMappers.SimpleDtoMapperImpl;
import com.santunioni.fixtures.dtoMappers.SimpleDtoOut;

public class SimpleService {
    private final UserMapper mapper = new UserMapper();

    public UserDto process(UserEntity input) {
        return mapper.toUserDto(input);
    }
}
