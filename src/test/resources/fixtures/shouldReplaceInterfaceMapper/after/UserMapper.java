package io.github.santunioni.fixtures;

import lombok.Setter;

public class UserMapper {
    public static final java.lang.String INTERFACE_FIELD = "VALUE";
    public static final java.lang.String FINAL_INTERFACE_FIELD = "VALUE";
    public static final java.lang.String STATIC_FINAL_INTERFACE_FIELD = "VALUE";

    @Setter
    private java.lang.Long childField;

    public UserMapper() {
    }

    static java.lang.String formatFullNameStatic(java.lang.String firstName, java.lang.String lastName) {
        return firstName + " " + lastName;
    }

    public UserEntity toUserEntity(UserDto userDto) {
        java.lang.String fullName = formatFullNameDefault(userDto.getFirstName(), userDto.getLastName());
        return new UserEntity(fullName);
    }

    public UserDto toUserDto(UserEntity userEntity) {
        java.lang.String fullName = userEntity.getFullName();
        int split = fullName.indexOf(' ');
        UserDto userDto = new UserDto(fullName.substring(0, split), fullName.substring(split + 1));
        setLastName(userDto, userEntity);
        return userDto;
    }

    public java.lang.String formatFullNameDefault(java.lang.String firstName, java.lang.String lastName) {
        return firstName + " " + lastName;
    }

    protected void setLastName(final UserDto userDto,
                               final UserEntity userEntity) {
        userDto.setLastName(userEntity.getFullName());
    }
}
