package com.marcelodev.emailservice.service;

import com.marcelodev.emailservice.domain.EmailModel;
import com.marcelodev.emailservice.domain.StatusEmail;
import com.marcelodev.emailservice.dto.SendEmailDto;
import com.marcelodev.emailservice.repository.EmailModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.time.Instant;

@Slf4j
@Service
public class EmailService {

    private final EmailModelRepository repository;
    private final SesV2Client ses;
    private final String from;

    public EmailService(EmailModelRepository repository, SesV2Client ses,
                        @Value("${email.from}") String from) {
        this.repository = repository;
        this.ses = ses;
        this.from = from;
    }

    public void send(SendEmailDto dto) {
        var email = new EmailModel(dto.eventId(), dto.ownerRef(), from, dto.to(),
                dto.subject(), dto.body(), Instant.now(), StatusEmail.PENDING);

        if (!repository.claim(email)) {
            log.info("evento {} já enviado ou em envio, ignorando mensagem duplicada", dto.eventId());
            return;
        }

        try {
            sendViaSes(dto);
            email.setStatusEmail(StatusEmail.SENT);
            log.info("email do evento {} enviado para {}", dto.eventId(), dto.to());
        } catch (SdkException e) {
            log.error("falha ao enviar email do evento {} para {}: {}", dto.eventId(), dto.to(), e.getMessage(), e);
            email.setStatusEmail(StatusEmail.FAILED);
            repository.save(email);
            throw e; // a mensagem volta para a fila (retry do Rabbit ou reentrega do SQS)
        }

        repository.save(email);
    }

    private void sendViaSes(SendEmailDto dto) {
        ses.sendEmail(r -> r
                .fromEmailAddress(from)
                .destination(d -> d.toAddresses(dto.to()))
                .content(c -> c.simple(m -> m
                        .subject(s -> s.data(dto.subject()))
                        .body(b -> b.text(t -> t.data(dto.body()))))));
    }
}
