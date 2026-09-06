package com.shopchain.service;

import com.shopchain.entity.SystemSetting;
import com.shopchain.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SystemSettingService {
    private final SystemSettingRepository settingRepository;

    public SystemSettingService(SystemSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public String getSetting(String key, String defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }

    public void setSetting(String key, String value) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElse(SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        settingRepository.save(setting);
    }
}
