package com.shopchain.controller.admin;

import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.SystemSettingRepository;
import com.shopchain.service.IntegrationConfigurationService;
import com.shopchain.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminSettingControllerTest {
    @Test
    void settingsPageShowsActiveStatusesButNeverReturnsSecretValues() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        List<SystemSetting> all = List.of(
                setting("smtp.pass", "smtp-super-secret"), setting("vnpay.hashSecret", "vnpay-super-secret"),
                setting("smtp.user", "otp@example.test"), setting("vnpay.tmnCode", "MERCHANT"));
        when(repository.findAll()).thenReturn(all);
        when(repository.findBySettingKey(anyString())).thenAnswer(call -> all.stream()
                .filter(setting -> setting.getSettingKey().equals(call.getArgument(0))).findFirst());
        IntegrationConfigurationService config = new IntegrationConfigurationService(
                new SystemSettingService(repository), "https://sandbox.test/pay", "https://shop.test/return",
                "", "", "smtp.env.test", 587, "env@example.test", "env-secret");
        AdminSettingController controller = new AdminSettingController(repository, config);
        ExtendedModelMap model = new ExtendedModelMap();

        controller.settingsPage(model);

        @SuppressWarnings("unchecked") Map<String, String> settings = (Map<String, String>) model.get("settings");
        assertThat(settings).containsEntry("smtp.user", "otp@example.test")
                .doesNotContainKey("smtp.pass").doesNotContainKey("vnpay.hashSecret");
        assertThat(model.get("smtpPasswordConfigured")).isEqualTo(true);
        assertThat(model.get("vnpaySecretConfigured")).isEqualTo(true);
        assertThat(model.toString()).doesNotContain("smtp-super-secret", "vnpay-super-secret", "env-secret");
    }

    @Test
    void blankSecretFieldsDoNotOverwriteExistingValues() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        AdminSettingController controller = new AdminSettingController(repository,
                mock(IntegrationConfigurationService.class));

        controller.saveSettings(Map.of("smtp.pass", "  ", "vnpay.hashSecret", ""));

        verify(repository, never()).save(any(SystemSetting.class));
        verify(repository, never()).findBySettingKey(anyString());
    }

    private SystemSetting setting(String key, String value) {
        return SystemSetting.builder().settingKey(key).settingValue(value).build();
    }
}
