package com.marcelodev.emailservice.consumer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.marcelodev.emailservice.EmailServiceApplication;
import com.marcelodev.emailservice.dto.SendEmailDto;
import com.marcelodev.emailservice.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;

@Slf4j
public class SqsConsumer implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final EmailService emailService;

    private final JsonMapper jsonMapper;

    public SqsConsumer() { // a Lambda chama no init; o Spring entra no snapshot do SnapStart
        var ctx = new SpringApplicationBuilder(EmailServiceApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
                        "spring.docker.compose.enabled=false")
                .run();
        this.emailService = ctx.getBean(EmailService.class);
        this.jsonMapper = ctx.getBean(JsonMapper.class);
    }

    SqsConsumer(EmailService emailService, JsonMapper jsonMapper) { // para testes, sem subir o Spring
        this.emailService = emailService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent input, Context context) {
        var failures = new ArrayList<SQSBatchResponse.BatchItemFailure>();
        for (var msg : input.getRecords()) {
            try {
                emailService.send(jsonMapper.readValue(msg.getBody(), SendEmailDto.class));
            } catch (Exception e) {
                log.error("falha ao processar a mensagem {}: {}", msg.getMessageId(), e.getMessage(), e);
                failures.add(new SQSBatchResponse.BatchItemFailure(msg.getMessageId()));
            }
        }
        return new SQSBatchResponse(failures);
    }
}
