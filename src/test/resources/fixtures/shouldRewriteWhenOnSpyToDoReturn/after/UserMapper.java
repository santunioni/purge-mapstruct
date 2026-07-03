package com.santunioni.fixtures;

public class UserMapper {

    public UserMapper() {
    }

    public UserDto toUserDto(UserEntity userEntity) {
        String fullName = userEntity.getFullName();
        int split = fullName.indexOf(' ');
        return new UserDto(fullName.substring(0, split), fullName.substring(split + 1));
    }
}
