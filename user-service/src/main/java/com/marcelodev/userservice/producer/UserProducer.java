package com.marcelodev.userservice.producer;


import com.marcelodev.userservice.domain.UserModel;
import com.marcelodev.userservice.dto.EmailDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@RequiredArgsConstructor
public class UserProducer {

    private final RabbitTemplate rabbitTemplate;

    private final String rountingKey = "email-queue";


    public void convertAndSend(UserModel user) {
        EmailDto emailDto = new EmailDto();
        emailDto.setId(user.getId());
        emailDto.setEmailTo(user.getEmail());
        emailDto.setSubject("Bem-vindo ao nosso sistema!");
        emailDto.setText("Olá " + user.getName() + ", obrigado por se cadastrar em nosso sistema!");

        rabbitTemplate.convertAndSend("",rountingKey,emailDto);
    }
}
