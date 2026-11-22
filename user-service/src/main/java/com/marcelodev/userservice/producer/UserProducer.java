package com.marcelodev.userservice.producer;


import com.marcelodev.userservice.domain.UserModel;
import com.marcelodev.userservice.dto.SendEmailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@Slf4j
@RequiredArgsConstructor
public class UserProducer {

    private final RabbitTemplate rabbitTemplate;

    private final String rountingKey = "email-queue";


    public void convertAndSend(UserModel user) {
        var message = new SendEmailDto(
                UUID.randomUUID(),
                user.getId(),
                user.getEmail(),
                "Bem-vindo ao nosso sistema!",
                "Olá " + user.getName() + ", obrigado por se cadastrar em nosso sistema!");

        rabbitTemplate.convertAndSend("", rountingKey, message);
    }
}
