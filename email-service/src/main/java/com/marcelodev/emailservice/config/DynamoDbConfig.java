package com.marcelodev.emailservice.config;

import com.marcelodev.emailservice.domain.EmailModel;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    private boolean local() {
        return !endpoint.isBlank();
    }

    @Bean
    DynamoDbClient dynamoDbClient(@Value("${aws.region}") String region) {
        var builder = DynamoDbClient.builder().region(Region.of(region));
        if (local()) {
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
    ApplicationRunner createLocalTable(DynamoDbEnhancedClient client, @Value("${aws.dynamodb.table}") String table) {
        return args -> {
            if (!local()) return;
            try {
                client.table(table, TableSchema.fromBean(EmailModel.class)).createTable();
            } catch (ResourceInUseException alreadyExists) {
            }
        };
    }
}
