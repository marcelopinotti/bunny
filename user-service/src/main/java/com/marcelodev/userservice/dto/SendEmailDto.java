package com.marcelodev.userservice.dto;

public record SendEmailDto(
        String eventId,
        String ownerRef,
        String to,
        String subject,
        String body
) {
}
