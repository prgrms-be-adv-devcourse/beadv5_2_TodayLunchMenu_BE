package com.example.member.infrastructure.email;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body, boolean html) {
        log.info("Sending email. to={}, subject={}, html={}, body={}", to, subject, html, body);
    }
}
