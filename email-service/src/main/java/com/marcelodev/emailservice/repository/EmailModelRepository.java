package com.marcelodev.emailservice.repository;

import com.marcelodev.emailservice.domain.EmailModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;
import java.util.UUID;

@Repository
public class EmailModelRepository {

    private final DynamoDbTable<EmailModel> table;

    public EmailModelRepository(DynamoDbEnhancedClient client, @Value("${aws.dynamodb.table}") String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(EmailModel.class));
    }

    public EmailModel save(EmailModel email) {
        table.putItem(email);
        return email;
    }

    public Optional<EmailModel> findById(UUID id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id.toString()).build()));
    }
}
