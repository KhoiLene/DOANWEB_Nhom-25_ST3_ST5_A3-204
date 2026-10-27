package com.shopchain.controller.admin;

import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.SystemSettingRepository;
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
            "zalo.accessToken", "zalo.appId", "vnpay.tmnCode", "vnpay.hashSecret",
            "payment.qrBankCode", "payment.qrAccountNumber", "payment.qrAccountName");
    private static final Set<String> SECRET_SETTINGS = Set.of("smtp.pass", "zalo.accessToken", "vnpay.hashSecret");

    private final SystemSettingRepository settingRepository;

    public AdminSettingController(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @GetMapping
    public String settingsPage(Model model) {
        Map<String, String> settings = new HashMap<>();
        boolean smtpPasswordConfigured = false;
        boolean zaloTokenConfigured = false;
        boolean vnpaySecretConfigured = false;
        for (SystemSetting setting : settingRepository.findAll()) {
            String key = setting.getSettingKey();
            if (!EDITABLE_SETTINGS.contains(key)) continue;
            if (SECRET_SETTINGS.contains(key)) {
                boolean configured = setting.getSettingValue() != null && !setting.getSettingValue().isBlank();
                if ("smtp.pass".equals(key)) smtpPasswordConfigured = configured;
                if ("zalo.accessToken".equals(key)) zaloTokenConfigured = configured;
                if ("vnpay.hashSecret".equals(key)) vnpaySecretConfigured = configured;
            } else {
                settings.put(key, setting.getSettingValue());
            }
        }

        model.addAttribute("settings", settings);
        model.addAttribute("smtpPasswordConfigured", smtpPasswordConfigured);
        model.addAttribute("zaloTokenConfigured", zaloTokenConfigured);
        model.addAttribute("vnpaySecretConfigured", vnpaySecretConfigured);
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
