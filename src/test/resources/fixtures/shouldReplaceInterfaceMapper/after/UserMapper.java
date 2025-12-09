package com.santunioni.fixtures;

import lombok.Setter;


public class UserMapper {
    public static final String INTERFACE_FIELD = "VALUE";
    public static final String FINAL_INTERFACE_FIELD = "VALUE";
    public static final String STATIC_FINAL_INTERFACE_FIELD = "VALUE";

    @Setter
    private Long childField;

    public UserMapper() {
    }

    static String formatFullName2(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    static String formatFullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    public UserEntity toUserEntity(UserDto userDto) {
        String fullName = formatFullName(userDto.getFirstName(), userDto.getLastName());
        return new UserEntity(fullName);
    }

    public UserDto toUserDto(UserEntity userEntity) {
        String fullName = userEntity.getFullName();
        int split = fullName.indexOf(' ');
        return new UserDto(fullName.substring(0, split), fullName.substring(split + 1));
    }
}