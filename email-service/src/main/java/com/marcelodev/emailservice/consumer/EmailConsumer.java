package com.marcelodev.emailservice.consumer;

import com.marcelodev.emailservice.dto.SendEmailDto;
import com.marcelodev.emailservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailService emailService;

    @RabbitListener(queues = "email-queue")
    public void consume(@Payload SendEmailDto dto) {
        emailService.send(dto);
    }
}
