package com.marcelodev.userservice.config;

import com.marcelodev.userservice.repository.UserModelRepository.EmailAlreadyUsedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ProblemDetail emailAlreadyUsed(EmailAlreadyUsedException e) {
        log.warn("Erro ao criar usuário: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(TransactionCanceledException.class)
    public ProblemDetail transactionCanceled(TransactionCanceledException e) {
        log.error("Transação do DynamoDB cancelada", e);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Não foi possível concluir a operação."
        );
    }
}
