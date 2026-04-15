package com.example.member.config;

import com.example.member.infrastructure.email.EmailSender;
import com.example.member.infrastructure.email.LoggingEmailSender;
import com.example.member.infrastructure.email.SmtpEmailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailSenderConfig {

    @Bean
    public EmailSender emailSender(JavaMailSender javaMailSender, EmailProperties emailProperties) {
        if (emailProperties.usesSmtp()) {
            return new SmtpEmailSender(javaMailSender, emailProperties);
        }
        return new LoggingEmailSender();
    }
}
