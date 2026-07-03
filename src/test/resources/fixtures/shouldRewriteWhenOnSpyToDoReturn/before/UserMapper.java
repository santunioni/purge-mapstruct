package com.santunioni.fixtures;

import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {
    UserDto toUserDto(UserEntity userEntity);
}
