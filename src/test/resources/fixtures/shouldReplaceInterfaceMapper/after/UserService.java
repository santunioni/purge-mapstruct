package com.santunioni.fixtures;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;

@ExtendWith(com.santunioni.fixtures.UserMapper.class)
@Extensions(classes = {
        UserMapper.class,
})
public class SimpleService {
    private final UserMapper mapper = new UserMapper();

    public UserDto process(UserEntity input) {
        return mapper.toUserDto(input);
    }
}