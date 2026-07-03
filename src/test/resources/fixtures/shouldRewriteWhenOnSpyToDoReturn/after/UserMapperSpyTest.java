package com.santunioni.fixtures;

import org.mockito.Spy;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class UserMapperSpyTest {
    @Spy
    private UserMapper mapper;

    public void stub(UserEntity input, UserDto expected) {
        
                doReturn(expected).when(mapper).toUserDto(input);
    }
}
