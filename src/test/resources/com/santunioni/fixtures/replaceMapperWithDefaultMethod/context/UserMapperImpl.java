package com.santunioni.fixtures.dtoMappers;

import javax.annotation.processing.Generated;

@Generated(value = "org.mapstruct.ap.MappingProcessor", date = "2025-01-01T00:00:00Z", comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17")
public class UserMapperImpl implements UserMapper {

    @Override
    public UserEntity toUserEntity(UserDto userDto) {
        if (userDto == null) {
            return null;
        }

        String fullName = formatFullName(userDto.getFirstName(), userDto.getLastName());
        return new UserEntity(fullName);
    }
}
