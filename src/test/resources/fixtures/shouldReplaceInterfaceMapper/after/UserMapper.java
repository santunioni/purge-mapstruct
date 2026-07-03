package io.github.santunioni.fixtures;

public class UserMapper {
    public static final java.lang.String INTERFACE_FIELD = "VALUE";
    public static final java.lang.String FINAL_INTERFACE_FIELD = "VALUE";
    public static final java.lang.String STATIC_FINAL_INTERFACE_FIELD = "VALUE";

    @lombok.Setter
    private java.lang.Long childField;

    public UserMapper() {
    }

    static java.lang.String formatFullNameStatic(java.lang.String firstName, java.lang.String lastName) {
        return firstName + " " + lastName;
    }

    public io.github.santunioni.fixtures.UserEntity toUserEntity(io.github.santunioni.fixtures.UserDto userDto) {
        java.lang.String fullName = formatFullNameDefault(userDto.getFirstName(), userDto.getLastName());
        return new io.github.santunioni.fixtures.UserEntity(fullName);
    }

    public io.github.santunioni.fixtures.UserDto toUserDto(io.github.santunioni.fixtures.UserEntity userEntity) {
        java.lang.String fullName = userEntity.getFullName();
        int split = fullName.indexOf(' ');
        io.github.santunioni.fixtures.UserDto userDto = new io.github.santunioni.fixtures.UserDto(fullName.substring(0, split), fullName.substring(split + 1));
        setLastName(userDto, userEntity);
        return userDto;
    }

    public java.lang.String formatFullNameDefault(java.lang.String firstName, java.lang.String lastName) {
        return firstName + " " + lastName;
    }

    protected void setLastName(final io.github.santunioni.fixtures.UserDto userDto,
                               final io.github.santunioni.fixtures.UserEntity userEntity) {
        userDto.setLastName(userEntity.getFullName());
    }
}
