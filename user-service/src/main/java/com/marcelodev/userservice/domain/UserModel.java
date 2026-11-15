package com.marcelodev.userservice.domain;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.UUID;

@DynamoDbBean
@Getter
@Setter
public class UserModel {


    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private UUID id;

    private String name;

    // atributo comum, não chave: pode mudar. Vai na mensagem como "to"
    private String email;


    private Instant createdAt;
}
