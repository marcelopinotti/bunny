package com.marcelodev.userservice.config;

import com.marcelodev.userservice.repository.UserModelRepository.EmailAlreadyUsedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.Map;
import java.util.stream.Collectors;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        f -> String.valueOf(f.getDefaultMessage()),
                        (first, second) -> first));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
        problem.setProperty("errors", errors);
        return problem;
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
