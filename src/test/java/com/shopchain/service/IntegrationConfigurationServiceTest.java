package com.shopchain.service;

import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IntegrationConfigurationServiceTest {
    @Test
    void databaseValuesTakePrecedenceAndEnvironmentFillsBlankFields() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        Map<String, String> database = Map.of(
                "vnpay.tmnCode", "DBMERCHANT", "vnpay.hashSecret", "db-vnp-secret",
                "smtp.host", "smtp.database.test", "smtp.port", "2525");
        when(repository.findBySettingKey(anyString())).thenAnswer(call -> {
            String value = database.get(call.getArgument(0));
            return value == null ? Optional.empty() : Optional.of(SystemSetting.builder()
                    .settingKey(call.getArgument(0)).settingValue(value).build());
        });
        IntegrationConfigurationService config = new IntegrationConfigurationService(
                new SystemSettingService(repository), "https://sandbox.vnpay.test/pay", "https://shop.test/return",
                "ENVMERCHANT", "env-vnp-secret", "smtp.env.test", 587, "otp@example.test", "env-smtp-secret");

        assertThat(config.vnPay().tmnCode()).isEqualTo("DBMERCHANT");
        assertThat(config.vnPay().hashSecret()).isEqualTo("db-vnp-secret");
        assertThat(config.smtp().host()).isEqualTo("smtp.database.test");
        assertThat(config.smtp().port()).isEqualTo(2525);
        assertThat(config.smtp().username()).isEqualTo("otp@example.test");
        assertThat(config.smtp().password()).isEqualTo("env-smtp-secret");
        assertThat(config.smtpStatus().source()).contains("Cơ sở dữ liệu");
    }

    @Test
    void environmentFallbackIsReportedWithoutReturningSecrets() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(anyString())).thenReturn(Optional.empty());
        IntegrationConfigurationService config = new IntegrationConfigurationService(
                new SystemSettingService(repository), "https://sandbox.vnpay.test/pay", "https://shop.test/return",
                "ENV_MERCHANT", "hidden-vnpay-secret", "smtp.env.test", 587,
                "otp@example.test", "hidden-smtp-secret");

        assertThat(config.vnPay().tmnCode()).isEqualTo("ENV_MERCHANT");
        assertThat(config.smtpStatus().configured()).isTrue();
        assertThat(config.smtpStatus().source()).isEqualTo("Biến môi trường");
        assertThat(config.vnPayStatus().secretConfigured()).isTrue();
        assertThat(config.smtpStatus().toString()).doesNotContain("hidden-smtp-secret");
        assertThat(config.vnPayStatus().toString()).doesNotContain("hidden-vnpay-secret");
    }
}
