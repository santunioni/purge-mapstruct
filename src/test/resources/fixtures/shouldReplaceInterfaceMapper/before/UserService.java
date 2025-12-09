package com.santunioni.fixtures;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;

@ExtendWith(UserMapperImpl.class)
@Extensions(value = {UserMapperImpl.class})
public class SimpleService {
    private final UserMapperImpl mapper = new UserMapperImpl();

    public UserDto process(UserEntity input) {
        return mapper.toUserDto(input);
    }
}
