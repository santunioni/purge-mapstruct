package com.santunioni.fixtures;

import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {
    String INTERFACE_FIELD = "VALUE";
    final String FINAL_INTERFACE_FIELD = "VALUE";
    static final String STATIC_FINAL_INTERFACE_FIELD = "VALUE";

    static String formatFullName2(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    UserEntity toUserEntity(UserDto userDto);

    UserDto toUserDto(UserEntity userEntity);

    default String formatFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
