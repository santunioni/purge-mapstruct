package io.github.santunioni.fixtures;

public class UserMapper {

    public UserMapper() {
    }

    public io.github.santunioni.fixtures.UserDto toUserDto(io.github.santunioni.fixtures.UserEntity userEntity) {
        java.lang.String fullName = userEntity.getFullName();
        int split = fullName.indexOf(' ');
        return new io.github.santunioni.fixtures.UserDto(fullName.substring(0, split), fullName.substring(split + 1));
    }
}
