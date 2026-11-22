package com.marcelodev.userservice.dto.mapper;

import com.marcelodev.userservice.domain.UserModel;
import com.marcelodev.userservice.dto.CreateUserDto;
import com.marcelodev.userservice.dto.UserResponseDto;


public final class UserMapper {
    private UserMapper() {
    }




    public static UserModel toModel(CreateUserDto dto) {
        var u = new UserModel();
        u.setName(dto.name());
        u.setEmail(dto.email());
        return u;
    }

    public static UserResponseDto toResponse(UserModel u) {
        return new UserResponseDto(u.getId(), u.getName(), u.getEmail(), u.getCreatedAt());
    }
}
