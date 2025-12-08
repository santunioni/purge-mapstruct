package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {
    UserEntity toUserEntity(UserDto userDto);

    default String formatFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    static String formatFullName2(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
