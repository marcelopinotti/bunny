package com.marcelodev.emailservice.consumer;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.marcelodev.emailservice.service.EmailService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SqsConsumerTest {

    // igual ao init da Lambda; precisa do DynamoDB Local rodando: docker compose up -d (na raiz)
    @Test
    void lambdaInitBootsSpringWithoutWebAndRabbit() {
        assertDoesNotThrow(() -> new SqsConsumer());
    }

    @Test
    void reportsOnlyFailedMessagesOfTheBatch() {
        var emailService = mock(EmailService.class);
        doThrow(SesV2Exception.builder().message("ses fora").build())
                .when(emailService).send(argThat(dto -> dto.subject().equals("falha")));
        var consumer = new SqsConsumer(emailService, JsonMapper.builder().build());

        var event = new SQSEvent();
        event.setRecords(List.of(
                message("m1", json("ok")),
                message("m2", json("falha")),
                message("m3", "isso não é json")));

        var response = consumer.handleRequest(event, null);

        var failedIds = response.getBatchItemFailures().stream()
                .map(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .toList();
        assertEquals(List.of("m2", "m3"), failedIds);
        verify(emailService, times(2)).send(argThat(dto -> true));
    }

    private static SQSEvent.SQSMessage message(String id, String body) {
        var msg = new SQSEvent.SQSMessage();
        msg.setMessageId(id);
        msg.setBody(body);
        return msg;
    }

    private static String json(String subject) {
        return """
                {"eventId":"%s","ownerRef":"%s","to":"to@x.com","subject":"%s","body":"corpo"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), subject);
    }
}
