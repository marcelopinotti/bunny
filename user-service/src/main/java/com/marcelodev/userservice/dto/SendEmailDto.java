package com.marcelodev.userservice.dto;

import java.util.UUID;


public record SendEmailDto(
        UUID eventId,
        UUID ownerRef,
        String to,
        String subject,
        String body
) {
}
