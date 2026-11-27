package com.marcelodev.emailservice.service;

import com.marcelodev.emailservice.domain.EmailModel;
import com.marcelodev.emailservice.domain.StatusEmail;
import com.marcelodev.emailservice.dto.SendEmailDto;
import com.marcelodev.emailservice.repository.EmailModelRepository;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.net.URI;
import java.util.UUID;
import java.util.function.Consumer;

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
    private final SesV2Client ses = mock(SesV2Client.class);
    private final EmailService service = new EmailService(repo, ses, "from@x.com");

    @Test
    void sendsOnceAndSavesSentEvenIfMessageArrivesTwice() {
        var dto = dto();

        service.send(dto);
        service.send(dto);

        verify(ses, times(1)).sendEmail(anyRequest());
        assertEquals(StatusEmail.SENT, repo.findById(dto.eventId()).orElseThrow().getStatusEmail());
    }

    @Test
    void savesFailedAndRethrowsWhenSesFailsThenRetrySends() {
        var dto = dto();
        doThrow(SesV2Exception.builder().message("ses fora").build())
                .doReturn(SendEmailResponse.builder().build())
                .when(ses).sendEmail(anyRequest());

        assertThrows(SesV2Exception.class, () -> service.send(dto));
        assertEquals(StatusEmail.FAILED, repo.findById(dto.eventId()).orElseThrow().getStatusEmail());

        service.send(dto);
        assertEquals(StatusEmail.SENT, repo.findById(dto.eventId()).orElseThrow().getStatusEmail());
        verify(ses, times(2)).sendEmail(anyRequest());
    }

    // o EmailService usa a sobrecarga sendEmail(Consumer<Builder>) do SDK
    private static Consumer<SendEmailRequest.Builder> anyRequest() {
        return any();
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
