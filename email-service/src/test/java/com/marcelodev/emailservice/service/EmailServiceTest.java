package com.marcelodev.emailservice.service;

import com.marcelodev.emailservice.domain.EmailModel;
import com.marcelodev.emailservice.domain.StatusEmail;
import com.marcelodev.emailservice.dto.SendEmailDto;
import com.marcelodev.emailservice.repository.EmailModelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Precisa do DynamoDB Local rodando: docker compose up -d (na raiz)
class EmailServiceTest {

    private final EmailModelRepository repo = repo();
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailService service = new EmailService(repo, mailSender, "from@x.com");

    @Test
    void sendsOnceAndSavesSentEvenIfMessageArrivesTwice() {
        var dto = dto();

        service.send(dto);
        service.send(dto);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(StatusEmail.SENT, repo.findById(dto.eventId()).orElseThrow().getStatusEmail());
    }

    @Test
    void savesFailedAndRethrowsWhenSmtpFailsThenRetrySends() {
        var dto = dto();
        doThrow(new MailSendException("smtp fora"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> service.send(dto));
        assertEquals(StatusEmail.FAILED, repo.findById(dto.eventId()).orElseThrow().getStatusEmail());

        service.send(dto);
        assertEquals(StatusEmail.SENT, repo.findById(dto.eventId()).orElseThrow().getStatusEmail());
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    private static SendEmailDto dto() {
        return new SendEmailDto(UUID.randomUUID(), UUID.randomUUID(), "to@x.com", "Oi", "corpo");
    }

    private static EmailModelRepository repo() {
        var client = DynamoDbEnhancedClient.builder().dynamoDbClient(DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:8000"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build()).build();
        try {
            client.table("emails-test", TableSchema.fromBean(EmailModel.class)).createTable();
        } catch (ResourceInUseException ignored) {
        }
        return new EmailModelRepository(client, "emails-test");
    }
}
