package com.marcelodev.emailservice.repository;

import com.marcelodev.emailservice.domain.EmailModel;
import com.marcelodev.emailservice.domain.StatusEmail;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Precisa do DynamoDB Local rodando: docker compose up -d (na raiz)
class EmailModelRepositoryTest {

    @Test
    void saveAndFind() {
        var client = DynamoDbEnhancedClient.builder().dynamoDbClient(DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:8000"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build()).build();
        try {
            client.table("emails-test", TableSchema.fromBean(EmailModel.class)).createTable();
        } catch (ResourceInUseException ignored) {
        }
        var repo = new EmailModelRepository(client, "emails-test");

        var email = new EmailModel(UUID.randomUUID(), UUID.randomUUID(), "from@x.com", "to@x.com",
                "Oi", "corpo", Instant.parse("2026-11-14T10:00:00Z"), StatusEmail.SENT);
        repo.save(email);

        var found = repo.findById(email.getId()).orElseThrow();
        assertEquals(email.getOwnerRef(), found.getOwnerRef());
        assertEquals(email.getSendDateEmail(), found.getSendDateEmail());
        assertEquals(StatusEmail.SENT, found.getStatusEmail());
    }
}
