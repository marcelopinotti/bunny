package com.marcelodev.userservice.repository;

import com.marcelodev.userservice.domain.UserModel;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Precisa do DynamoDB Local rodando: docker compose up -d (na raiz)
class UserModelRepositoryTest {

    @Test
    void emailIsUnique() {
        var raw = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:8000"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
        var enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(raw).build();
        try {
            enhanced.table("users-test", TableSchema.fromBean(UserModel.class)).createTable();
        } catch (ResourceInUseException ignored) {
        }
        var repo = new UserModelRepository(raw, enhanced, "users-test");

        var email = UUID.randomUUID() + "@x.com";
        var first = user(email);
        repo.save(first);
        assertEquals(email, repo.findById(first.getId()).orElseThrow().getEmail());

        // mesmo email com outra caixa e espaços: tem que ser barrado, e o segundo usuário não pode ter sido gravado
        var dup = user("  " + email.toUpperCase() + " ");
        assertThrows(UserModelRepository.EmailAlreadyUsedException.class, () -> repo.save(dup));
        assertTrue(repo.findById(dup.getId()).isEmpty());
    }

    private static UserModel user(String email) {
        var u = new UserModel();
        u.setId(UUID.randomUUID());
        u.setName("Fulano");
        u.setEmail(email);
        u.setCreatedAt(Instant.parse("2026-11-14T10:00:00Z"));
        return u;
    }
}
