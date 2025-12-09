package com.santunioni.fixtures;

import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(
        UserMapperImpl.class
)
public class SimpleService {
    private final UserMapperImpl mapper = new UserMapperImpl();

    public UserDto process(UserEntity input) {
        return mapper.toUserDto(input);
    }
}
