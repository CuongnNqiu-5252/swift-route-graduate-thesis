package com.swiftroute.notificationservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailNotificationSender {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:}")
    private String from;

    public void send(String recipient, String subject, String body) {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("SMTP_FROM must be configured before email delivery is enabled");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
