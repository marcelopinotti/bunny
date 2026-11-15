package com.marcelodev.userservice.repository;

import com.marcelodev.userservice.domain.UserModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserModelRepository {

    private static final String NOT_EXISTS = "attribute_not_exists(id)";

    private final DynamoDbClient client;
    private final DynamoDbTable<UserModel> table;

    public UserModelRepository(DynamoDbClient client, DynamoDbEnhancedClient enhanced,
                               @Value("${aws.dynamodb.table}") String tableName) {
        this.client = client;
        this.table = enhanced.table(tableName, TableSchema.fromBean(UserModel.class));
    }

    // DynamoDB não tem UNIQUE: grava o usuário e um item "EMAIL#<email>" na mesma transação.
    // Se o item do email já existe, a transação inteira falha e nada é gravado.
    public UserModel save(UserModel user) {
        var userPut = TransactWriteItem.builder()
                .put(p -> p
                .tableName(table.tableName())
                .item(table.tableSchema().itemToMap(user, true))
                .conditionExpression(NOT_EXISTS)).build();
        var emailPut = TransactWriteItem.builder().put(p -> p
                .tableName(table.tableName())
                .item(Map.of(
                        "id", AttributeValue.fromS(emailKey(user.getEmail())),
                        "userId", AttributeValue.fromS(user.getId().toString())))
                .conditionExpression(NOT_EXISTS)).build();

        try {
            client.transactWriteItems(r -> r.transactItems(userPut, emailPut));
        } catch (TransactionCanceledException e) {
            throw new EmailAlreadyUsedException(user.getEmail());
        }
        return user;
    }

    public Optional<UserModel> findById(UUID id) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(id.toString())
                .build()));
    }

    private static String emailKey(String email) {
        return "EMAIL#" + email.trim().toLowerCase(Locale.ROOT);
    }

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String email) {
            super("email já cadastrado: " + email);
        }
    }
}
