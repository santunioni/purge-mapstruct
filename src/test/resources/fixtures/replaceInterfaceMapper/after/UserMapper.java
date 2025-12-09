package com.santunioni.fixtures.dtoMappers;

import lombok.Setter;


public class UserMapper {

    @Setter
    private Long myField;


    public static final String STATUS = "PENDING";
    public static final String DEFAULT_STATUS = "PENDING";
    public static final String DEFAULT_SITUATION = "PENDING";

    public UserMapper() {
    }

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
