package com.marcelodev.emailservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
public class EmailModel {

    // eventId da mensagem: chave de idempotência
    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private UUID id;

    private UUID ownerRef;

    private String emailFrom;

    private String emailTo;

    private String subject;

    private String text;

    private Instant sendDateEmail;

    private StatusEmail statusEmail;

}
