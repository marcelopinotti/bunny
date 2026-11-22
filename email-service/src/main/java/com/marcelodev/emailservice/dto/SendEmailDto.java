package com.marcelodev.emailservice.dto;

import java.util.UUID;

public record SendEmailDto(
        UUID eventId,
        UUID ownerRef,
        String to,
        String subject,
        String body
) {
}
