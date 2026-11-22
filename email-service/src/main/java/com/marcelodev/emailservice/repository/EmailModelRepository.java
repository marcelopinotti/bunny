package com.marcelodev.emailservice.repository;

import com.marcelodev.emailservice.domain.EmailModel;
import com.marcelodev.emailservice.domain.StatusEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

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

    // grava se o eventId é novo ou falhou antes; bloqueia SENT e PENDING (já enviado ou em envio)
    public boolean claim(EmailModel email) {
        var condition = Expression.builder()
                .expression("attribute_not_exists(id) OR statusEmail = :failed")
                .putExpressionValue(":failed", AttributeValue.fromS(StatusEmail.FAILED.name()))
                .build();
        try {
            table.putItem(r -> r.item(email).conditionExpression(condition));
            return true;
        } catch (ConditionalCheckFailedException alreadySentOrInProgress) {
            return false;
        }
    }

    public Optional<EmailModel> findById(UUID id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id.toString()).build()));
    }
}
