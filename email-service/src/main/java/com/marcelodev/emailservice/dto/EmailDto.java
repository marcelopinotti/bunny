package com.marcelodev.emailservice.dto;

import java.util.UUID;

public record EmailDto(
        UUID id,
        String emailTo,
        String subject,
        String text
) {
}
