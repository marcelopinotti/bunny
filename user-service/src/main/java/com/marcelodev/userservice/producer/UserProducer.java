package com.marcelodev.userservice.producer;


import com.marcelodev.userservice.domain.UserModel;
import com.marcelodev.userservice.dto.SendEmailDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;


@Component
@Slf4j
public class UserProducer {

    private final SqsClient sqs;
    private final JsonMapper json;
    private final String queueUrl;

    public UserProducer(SqsClient sqs, JsonMapper json, @Value("${aws.sqs.email-queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.json = json;
        this.queueUrl = queueUrl;
    }

    public void convertAndSend(UserModel user) {
        var message = new SendEmailDto(
                UUID.randomUUID(),
                user.getId(),
                user.getEmail(),
                "Bem-vindo ao nosso sistema!",
                "Olá " + user.getName() + ", obrigado por se cadastrar em nosso sistema!");

        var response = sqs.sendMessage(r -> r.queueUrl(queueUrl).messageBody(json.writeValueAsString(message)));
        log.info("Evento {} enviado ao SQS (messageId {})", message.eventId(), response.messageId());
    }
}
