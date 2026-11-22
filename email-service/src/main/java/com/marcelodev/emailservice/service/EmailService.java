package com.marcelodev.emailservice.service;

import com.marcelodev.emailservice.domain.EmailModel;
import com.marcelodev.emailservice.domain.StatusEmail;
import com.marcelodev.emailservice.dto.SendEmailDto;
import com.marcelodev.emailservice.repository.EmailModelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class EmailService {

    private final EmailModelRepository repository;
    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(EmailModelRepository repository, JavaMailSender mailSender,
                        @Value("${email.from}") String from) {
        this.repository = repository;
        this.mailSender = mailSender;
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
            mailSender.send(toMailMessage(dto));
            email.setStatusEmail(StatusEmail.SENT);
            log.info("email do evento {} enviado para {}", dto.eventId(), dto.to());
        } catch (MailException e) {
            log.error("falha ao enviar email do evento {} para {}: {}", dto.eventId(), dto.to(), e.getMessage(), e);
            email.setStatusEmail(StatusEmail.FAILED);
            repository.save(email);
            throw e; // o RabbitMQ entrega de novo (retry no application.yaml)
        }

        repository.save(email);
    }

    private SimpleMailMessage toMailMessage(SendEmailDto dto) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(dto.to());
        message.setSubject(dto.subject());
        message.setText(dto.body());
        return message;
    }
}
