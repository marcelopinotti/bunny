package com.marcelodev.userservice.dto;


import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(UUID id, String name,  String email, Instant createdAt) {
}
