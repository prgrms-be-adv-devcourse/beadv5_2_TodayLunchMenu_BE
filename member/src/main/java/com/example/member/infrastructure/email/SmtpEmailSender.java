package com.example.member.infrastructure.email;

import com.example.member.common.exception.EmailSendFailedException;
import com.example.member.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    private final EmailProperties emailProperties;

    @Override
    public void send(String to, String subject, String body, boolean html) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, html);
            helper.setFrom(emailProperties.fromAddress(), emailProperties.fromName());
            javaMailSender.send(mimeMessage);
            log.info("Email sent via SMTP. to={}, subject={}, html={}", to, subject, html);
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            log.error("Failed to send email via SMTP. to={}, subject={}", to, subject, ex);
            throw new EmailSendFailedException("Failed to send email via SMTP.", ex);
        }
    }
}
