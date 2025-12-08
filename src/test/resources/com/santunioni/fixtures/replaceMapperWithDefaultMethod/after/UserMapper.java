package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;


public class UserMapper {


    public UserEntity toUserEntity(UserDto userDto) {
        if (userDto == null) {
            return null;
        }

        String fullName = formatFullName(userDto.getFirstName(), userDto.getLastName());
        return new UserEntity(fullName);
    }

    static String formatFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    static String formatFullName2(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
