package com.shopchain.service;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SmtpMailServiceTest {
    @Test
    void sendsOtpUsingEffectiveConfigurationWithoutCallingExternalServiceInTest() {
        IntegrationConfigurationService config = mock(IntegrationConfigurationService.class);
        when(config.smtp()).thenReturn(new IntegrationConfigurationService.SmtpSettings(
                "smtp.test", 2525, "sender@example.test", "secret-value"));
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpMailService mail = new SmtpMailService(config) {
            @Override
            protected JavaMailSender createSender(IntegrationConfigurationService.SmtpSettings active) {
                return sender;
            }
        };

        mail.send("customer@example.test", "OTP", "123456");

        var sent = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo("sender@example.test");
        assertThat(sent.getValue().getTo()).containsExactly("customer@example.test");
        assertThat(sent.getValue().getSubject()).isEqualTo("OTP");
        assertThat(sent.getValue().getText()).isEqualTo("123456");
    }

    @Test
    void doesNotSendWhenEffectiveSmtpCredentialsAreMissing() {
        IntegrationConfigurationService config = mock(IntegrationConfigurationService.class);
        when(config.smtp()).thenReturn(new IntegrationConfigurationService.SmtpSettings("smtp.test", 587, "", ""));
        SmtpMailService mail = new SmtpMailService(config);

        assertThatThrownBy(() -> mail.send("customer@example.test", "OTP", "123456"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SMTP");
    }
}
