package com.shopchain.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class SmtpMailService {
    private final IntegrationConfigurationService configuration;

    public SmtpMailService(IntegrationConfigurationService configuration) {
        this.configuration = configuration;
    }

    public void send(String to, String subject, String body) {
        IntegrationConfigurationService.SmtpSettings smtp = configuration.smtp();
        if (smtp.username().isBlank() || smtp.password().isBlank())
            throw new IllegalStateException("Chưa cấu hình SMTP. Thiết lập MAIL_USERNAME/MAIL_PASSWORD hoặc SMTP tại Cài đặt hệ thống.");
        JavaMailSender sender = createSender(smtp);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtp.username());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    protected JavaMailSender createSender(IntegrationConfigurationService.SmtpSettings smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        sender.setUsername(smtp.username());
        sender.setPassword(smtp.password());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
