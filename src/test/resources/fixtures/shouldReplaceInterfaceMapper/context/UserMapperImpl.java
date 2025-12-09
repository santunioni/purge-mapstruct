package com.santunioni.fixtures;

import lombok.Setter;
import org.mapstruct.AfterMapping;
import org.mapstruct.MappingTarget;

import javax.annotation.processing.Generated;

@Generated(
        value = "org.mapstruct.ap.MappingProcessor",
        date = "2025-01-01T00:00:00Z",
        comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17"
)
public class UserMapperImpl implements UserMapper {

    @Setter
    private Long childField;

    public UserMapperImpl() {
    }

    @Override
    public UserEntity toUserEntity(UserDto userDto) {
        String fullName = formatFullNameDefault(userDto.getFirstName(), userDto.getLastName());
        return new UserEntity(fullName);
    }

    @Override
    public UserDto toUserDto(UserEntity userEntity) {
        String fullName = userEntity.getFullName();
        int split = fullName.indexOf(' ');
        UserDto userDto = new UserDto(fullName.substring(0, split), fullName.substring(split + 1));
        setLastName(userDto, userEntity);
        return userDto;
    }

    @AfterMapping
    protected void setLastName(@MappingTarget final UserDto userDto,
                               final UserEntity userEntity) {
        userDto.setLastName(userEntity.getFullName());
    }
}
