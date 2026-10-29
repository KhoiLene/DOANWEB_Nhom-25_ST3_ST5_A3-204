package com.shopchain.controller.admin;

import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.SystemSettingRepository;
import com.shopchain.service.IntegrationConfigurationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingController {
    private static final Set<String> EDITABLE_SETTINGS = Set.of(
            "smtp.host", "smtp.port", "smtp.user", "smtp.pass",
            "vnpay.tmnCode", "vnpay.hashSecret",
            "payment.qrBankCode", "payment.qrAccountNumber", "payment.qrAccountName");
    private static final Set<String> SECRET_SETTINGS = Set.of("smtp.pass", "vnpay.hashSecret");

    private final SystemSettingRepository settingRepository;
    private final IntegrationConfigurationService integrationConfiguration;

    public AdminSettingController(SystemSettingRepository settingRepository,
                                  IntegrationConfigurationService integrationConfiguration) {
        this.settingRepository = settingRepository;
        this.integrationConfiguration = integrationConfiguration;
    }

    @GetMapping
    public String settingsPage(Model model) {
        Map<String, String> settings = new HashMap<>();
        boolean smtpPasswordConfigured = false;
        IntegrationConfigurationService.SmtpStatus smtpStatus = integrationConfiguration.smtpStatus();
        IntegrationConfigurationService.VnPayStatus vnPayStatus = integrationConfiguration.vnPayStatus();
        boolean vnpaySecretConfigured = vnPayStatus.secretConfigured();
        for (SystemSetting setting : settingRepository.findAll()) {
            String key = setting.getSettingKey();
            if (!EDITABLE_SETTINGS.contains(key)) continue;
            if (SECRET_SETTINGS.contains(key)) {
                boolean configured = setting.getSettingValue() != null && !setting.getSettingValue().isBlank();
                if ("smtp.pass".equals(key)) smtpPasswordConfigured = configured;
            } else {
                settings.put(key, setting.getSettingValue());
            }
        }

        smtpPasswordConfigured = smtpStatus.configured();

        model.addAttribute("settings", settings);
        model.addAttribute("smtpPasswordConfigured", smtpPasswordConfigured);
        model.addAttribute("vnpaySecretConfigured", vnpaySecretConfigured);
        model.addAttribute("smtpStatus", smtpStatus);
        model.addAttribute("vnPayStatus", vnPayStatus);
        model.addAttribute("title", "Cài đặt hệ thống");
        model.addAttribute("activePage", "settings");
        model.addAttribute("content", "admin/settings");
        return "admin/layout";
    }

    @PostMapping("/save")
    public String saveSettings(@RequestParam Map<String, String> allParams) {
        // Filter out internal Spring params like _csrf
        allParams.forEach((key, value) -> {
            if (!EDITABLE_SETTINGS.contains(key) || value == null || value.length() > 2048) return;
            if (SECRET_SETTINGS.contains(key) && value.isBlank()) return;
            SystemSetting setting = settingRepository.findBySettingKey(key)
                    .orElse(SystemSetting.builder().settingKey(key).build());
            setting.setSettingValue(value.trim());
            settingRepository.save(setting);
        });
        return "redirect:/admin/settings";
    }
}
