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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Precisa do DynamoDB Local rodando: docker compose up -d (na raiz)
class UserModelRepositoryTest {

    @Test
    void emailIsUnique() {
        var repo = repo();

        var email = UUID.randomUUID() + "@x.com";
        var first = user(email);
        repo.save(first);
        assertEquals(email, repo.findById(first.getId()).orElseThrow().getEmail());

        var dup = user("  " + email.toUpperCase() + " ");
        assertThrows(UserModelRepository.EmailAlreadyUsedException.class, () -> repo.save(dup));
        assertTrue(repo.findById(dup.getId()).isEmpty());
    }

    @Test
    void deleteByIdRemovesUserAndFreesEmail() {
        var repo = repo();
        var email = UUID.randomUUID() + "@x.com";
        var first = user(email);
        repo.save(first);

        assertTrue(repo.deleteById(first.getId()));
        assertTrue(repo.findById(first.getId()).isEmpty());

        var again = user(email);
        repo.save(again);
        assertTrue(repo.findById(again.getId()).isPresent());
    }

    @Test
    void deleteByIdReturnsFalseWhenUserDoesNotExist() {
        assertFalse(repo().deleteById(UUID.randomUUID()));
    }

    private static UserModelRepository repo() {
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
        return new UserModelRepository(raw, enhanced, "users-test");
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
