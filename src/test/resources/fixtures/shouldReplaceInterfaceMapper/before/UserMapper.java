package com.santunioni.fixtures;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper
public interface UserMapper {
    String INTERFACE_FIELD = "VALUE";
    final String FINAL_INTERFACE_FIELD = "VALUE";
    static final String STATIC_FINAL_INTERFACE_FIELD = "VALUE";

    static String formatFullNameStatic(String firstName, String lastName) {
        return firstName + " " + lastName;
    }

    UserEntity toUserEntity(UserDto userDto);

    UserDto toUserDto(UserEntity userEntity);

    @AfterMapping
    protected void setLastName(@MappingTarget final UserDto userDto,
                               final UserEntity userEntity) {
        userDto.setLastName(userEntity.getFullName());
    }

    default String formatFullNameDefault(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
