package com.marcelodev.userservice.dto;

import com.marcelodev.userservice.domain.UserModel;

import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(UUID id, String name, String email, Instant createdAt) {
    public static UserResponseDto from(UserModel u) {
        return new UserResponseDto(u.getId(), u.getName(), u.getEmail(), u.getCreatedAt());
    }
}

