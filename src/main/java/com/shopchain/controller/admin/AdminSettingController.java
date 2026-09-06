package com.shopchain.controller.admin;

import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.SystemSettingRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingController {

    private final SystemSettingRepository settingRepository;

    public AdminSettingController(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @GetMapping
    public String settingsPage(Model model) {
        Map<String, String> settings = new HashMap<>();
        settingRepository.findAll().forEach(s -> settings.put(s.getSettingKey(), s.getSettingValue()));

        model.addAttribute("settings", settings);
        model.addAttribute("title", "Cài đặt hệ thống");
        model.addAttribute("activePage", "settings");
        model.addAttribute("content", "admin/settings");
        return "admin/layout";
    }

    @PostMapping("/save")
    public String saveSettings(@RequestParam Map<String, String> allParams) {
        // Filter out internal Spring params like _csrf
        allParams.forEach((key, value) -> {
            if (!key.equals("_csrf")) {
                SystemSetting setting = settingRepository.findBySettingKey(key)
                        .orElse(SystemSetting.builder().settingKey(key).build());
                setting.setSettingValue(value);
                settingRepository.save(setting);
            }
        });
        return "redirect:/admin/settings";
    }
}
