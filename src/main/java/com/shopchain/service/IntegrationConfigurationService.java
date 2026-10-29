package com.shopchain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IntegrationConfigurationService {
    private final SystemSettingService settings;
    private final String envVnPayUrl;
    private final String envVnPayReturnUrl;
    private final String envVnPayTmnCode;
    private final String envVnPayHashSecret;
    private final String envSmtpHost;
    private final int envSmtpPort;
    private final String envSmtpUsername;
    private final String envSmtpPassword;

    public IntegrationConfigurationService(SystemSettingService settings,
            @Value("${vnpay.vnp_url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String envVnPayUrl,
            @Value("${vnpay.return_url:http://localhost:8080/api/payment/vnpay-return}") String envVnPayReturnUrl,
            @Value("${vnpay.tmn_code:}") String envVnPayTmnCode,
            @Value("${vnpay.hash_secret:}") String envVnPayHashSecret,
            @Value("${spring.mail.host:smtp.gmail.com}") String envSmtpHost,
            @Value("${spring.mail.port:587}") int envSmtpPort,
            @Value("${spring.mail.username:}") String envSmtpUsername,
            @Value("${spring.mail.password:}") String envSmtpPassword) {
        this.settings = settings;
        this.envVnPayUrl = envVnPayUrl;
        this.envVnPayReturnUrl = envVnPayReturnUrl;
        this.envVnPayTmnCode = envVnPayTmnCode;
        this.envVnPayHashSecret = envVnPayHashSecret;
        this.envSmtpHost = envSmtpHost;
        this.envSmtpPort = envSmtpPort;
        this.envSmtpUsername = envSmtpUsername;
        this.envSmtpPassword = envSmtpPassword;
    }

    public VnPaySettings vnPay() {
        return new VnPaySettings(
                firstConfigured("vnpay.vnpUrl", envVnPayUrl),
                firstConfigured("vnpay.returnUrl", envVnPayReturnUrl),
                firstConfigured("vnpay.tmnCode", envVnPayTmnCode),
                firstConfigured("vnpay.hashSecret", envVnPayHashSecret));
    }

    public SmtpSettings smtp() {
        String host = firstConfigured("smtp.host", envSmtpHost);
        String portValue = firstConfigured("smtp.port", Integer.toString(envSmtpPort));
        int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Cấu hình SMTP port không hợp lệ.");
        }
        if (port < 1 || port > 65535) throw new IllegalStateException("Cấu hình SMTP port không hợp lệ.");
        return new SmtpSettings(host, port,
                firstConfigured("smtp.user", envSmtpUsername),
                firstConfigured("smtp.pass", envSmtpPassword));
    }

    public SmtpStatus smtpStatus() {
        SmtpSettings active = smtp();
        boolean configured = !active.username().isBlank() && !active.password().isBlank();
        boolean usesDatabase = hasSetting("smtp.host") || hasSetting("smtp.port")
                || hasSetting("smtp.user") || hasSetting("smtp.pass");
        return new SmtpStatus(active.host(), active.port(), active.username(), configured,
                usesDatabase ? "Cơ sở dữ liệu (ưu tiên)" : "Biến môi trường");
    }

    public VnPayStatus vnPayStatus() {
        VnPaySettings active = vnPay();
        boolean usesDatabase = hasSetting("vnpay.tmnCode") || hasSetting("vnpay.hashSecret")
                || hasSetting("vnpay.vnpUrl") || hasSetting("vnpay.returnUrl");
        return new VnPayStatus(active.url(), active.returnUrl(), active.tmnCode(),
                !active.hashSecret().isBlank(), usesDatabase ? "Cơ sở dữ liệu (ưu tiên)" : "Biến môi trường");
    }

    private String firstConfigured(String key, String fallback) {
        String databaseValue = settings.getSetting(key, "");
        return databaseValue == null || databaseValue.isBlank() ? nullToEmpty(fallback) : databaseValue.trim();
    }

    private boolean hasSetting(String key) {
        String value = settings.getSetting(key, "");
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) { return value == null ? "" : value.trim(); }

    public record VnPaySettings(String url, String returnUrl, String tmnCode, String hashSecret) { }
    public record SmtpSettings(String host, int port, String username, String password) { }
    public record SmtpStatus(String host, int port, String username, boolean configured, String source) { }
    public record VnPayStatus(String url, String returnUrl, String tmnCode, boolean secretConfigured, String source) { }
}
