package com.marcelodev.emailservice.consumer;

import com.marcelodev.emailservice.dto.SendEmailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class EmailConsumer {


    @RabbitListener(queues = "email-queue")
    public void consume(@Payload SendEmailDto dto) {
        log.info("Received message: {}", dto);
    }

}
