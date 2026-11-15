package com.marcelodev.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import com.marcelodev.userservice.domain.UserModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${aws.region}") String region,
                                  @Value("${aws.dynamodb.endpoint:}") String endpoint) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            // DynamoDB Local aceita qualquer credencial; na AWS vale a cadeia padrão (IAM role)
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }

    @Bean
    DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    ApplicationRunner createLocalTable(DynamoDbEnhancedClient client,
                                       @Value("${aws.dynamodb.table}") String table,
                                       @Value("${aws.dynamodb.endpoint:}") String endpoint) {
        return args -> {
            if (endpoint.isBlank()) return;
            try {
                client.table(table, TableSchema.fromBean(UserModel.class)).createTable();
            } catch (ResourceInUseException alreadyExists) {
                // tabela já existe
            }
        };
    }
}
