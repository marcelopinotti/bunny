package com.marcelodev.userservice.producer;


import com.marcelodev.userservice.domain.UserModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RequiredArgsConstructor
public class UserProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishWelcomeEmail(UserModel user) {
        log.info("Enviando mensagem para fila de email: {}", user);
        rabbitTemplate.convertAndSend("email-queue", user);
    }
}
