package com.santunioni.fixtures.dtoMappers;

import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {
    UserEntity toUserEntity(UserDto userDto);
    UserDto toUserDto(UserEntity userEntity);

    default String formatFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    static String formatFullName2(String firstName, String lastName) {
        return firstName + " " + lastName;
    }


    String STATUS = "PENDING";
    final String DEFAULT_STATUS = "PENDING";
    static final String DEFAULT_SITUATION = "PENDING";
}
