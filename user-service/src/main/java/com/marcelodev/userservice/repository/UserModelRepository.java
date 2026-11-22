package com.marcelodev.userservice.repository;

import com.marcelodev.userservice.domain.UserModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserModelRepository {

    private static final String NOT_EXISTS = "attribute_not_exists(id)";

    private static final String CONDITION_FAILED = "ConditionalCheckFailed";
    private final DynamoDbClient client;
    private final DynamoDbTable<UserModel> table;

    public UserModelRepository(DynamoDbClient client, DynamoDbEnhancedClient enhanced,
                               @Value("${aws.dynamodb.table}") String tableName) {
        this.client = client;
        this.table = enhanced.table(tableName, TableSchema.fromBean(UserModel.class));
    }

    // DynamoDB não tem UNIQUE: o marcador EMAIL#<email> é gravado na mesma transação do usuário
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
            // só é email duplicado se a condição do marcador (item 1) falhou
            if (conditionFailed(e, 1)) throw new EmailAlreadyUsedException(user.getEmail());
            throw e;
        }
        return user;
    }

    public Optional<UserModel> findById(UUID id) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(id.toString())
                .build()));
    }

    // exclui os marcadores EMAIL#, que não têm createdAt
    public List<UserModel> findAll() {
        var onlyUsers = Expression.builder().expression("attribute_exists(createdAt)").build();
        return table.scan(r -> r.filterExpression(onlyUsers)).items().stream().toList();
    }

    public boolean deleteById(UUID id) {
        Optional<UserModel> user = findById(id);
        if (user.isEmpty()) return false;
        String email = user.get().getEmail();
        // garante que o usuário não mudou entre a leitura e o delete
        var userDelete = TransactWriteItem.builder().delete(d -> d.tableName(table.tableName())
                .key(Map.of("id", AttributeValue.fromS(id.toString())))
                .conditionExpression("email = :email")
                .expressionAttributeValues(Map.of(":email", AttributeValue.fromS(email)))).build();
        var emailDelete = TransactWriteItem.builder().delete(d -> d.tableName(table.tableName())
                .key(Map.of("id", AttributeValue.fromS(emailKey(email))))).build();
        try {
            client.transactWriteItems(r -> r.transactItems(userDelete, emailDelete));
        } catch (TransactionCanceledException e) {
            if (conditionFailed(e, 0)) return false;
            throw e;
        }
        return true;
    }

    private static boolean conditionFailed(TransactionCanceledException e, int itemIndex) {
        var reasons = e.cancellationReasons();
        return reasons.size() > itemIndex && CONDITION_FAILED.equals(reasons.get(itemIndex).code());
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
