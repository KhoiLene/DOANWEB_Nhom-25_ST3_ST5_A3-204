package com.shopchain.service.impl;

import com.shopchain.service.PaymentService;
import com.shopchain.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${vnpay.vnp_url}")
    private String vnpUrl;
    @Value("${vnpay.return_url}")
    private String returnUrl;

    private final SystemSettingService settingService;

    public PaymentServiceImpl(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public String createVnPayPaymentUrl(Long orderId, Double amount, String clientIp) {
        String tmnCode = settingService.getSetting("vnpay.tmnCode", "");
        String hashSecret = settingService.getSetting("vnpay.hashSecret", "");
        if (tmnCode.isBlank() || hashSecret.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình thông tin kết nối VNPay.");
        }
        if (orderId == null || orderId <= 0 || amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Thông tin thanh toán không hợp lệ.");
        }

        String vnp_TxnRef = String.valueOf(orderId);
        String vnp_Amount = String.valueOf(BigDecimal.valueOf(amount).movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP).longValueExact());
        String vnp_Command = "pay";
        String vnp_CurrCode = "VND";
        String vnp_Version = "2.1.0";
        String vnp_OrderInfo = "Payment for Order ID: " + orderId;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        DateTimeFormatter vnpDateFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", vnp_TxnRef);
        params.put("vnp_Amount", vnp_Amount);
        params.put("vnp_Command", vnp_Command);
        params.put("vnp_CurrCode", vnp_CurrCode);
        params.put("vnp_Version", vnp_Version);
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_OrderInfo", vnp_OrderInfo);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_BankCode", "VNPAYQR");
        params.put("vnp_CreateDate", now.format(vnpDateFormat));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(vnpDateFormat));
        params.put("vnp_IpAddr", normalizeIpAddress(clientIp));
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderType", "other");

        // Sắp xếp tham số theo alphabet (yêu cầu của VNPay)
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder query = new StringBuilder();
        for (String key : sortedKeys) {
            if (query.length() > 0) query.append("&");
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                 .append("=")
                 .append(URLEncoder.encode(params.get(key), StandardCharsets.UTF_8));
        }

        String vnp_SecureHash = hmacSHA512(query.toString(), hashSecret);
        return vnpUrl + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;
    }

    private String normalizeIpAddress(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return "127.0.0.1";
        String candidate = clientIp.trim();
        if (candidate.length() > 45 || !candidate.matches("[0-9a-fA-F:.]+")) return "127.0.0.1";
        return candidate;
    }

    @Override
    public boolean verifyVnPayPayment(Map<String, String> params) {
        String hashSecret = settingService.getSetting("vnpay.hashSecret", "");
        String expectedMerchantCode = settingService.getSetting("vnpay.tmnCode", "");
        if (hashSecret.isBlank() || expectedMerchantCode.isBlank() || params == null
                || !expectedMerchantCode.equals(params.get("vnp_TmnCode"))) {
            return false;
        }
        String vnp_SecureHash = params.get("vnp_SecureHash");
        if (vnp_SecureHash == null || !vnp_SecureHash.matches("[a-fA-F0-9]{128}")) {
            return false;
        }

        Map<String, String> signedParams = new HashMap<>(params);
        signedParams.remove("vnp_SecureHash");
        signedParams.remove("vnp_SecureHashType");

        List<String> sortedKeys = new ArrayList<>(signedParams.keySet());
        Collections.sort(sortedKeys);

        StringBuilder query = new StringBuilder();
        for (String key : sortedKeys) {
            if (query.length() > 0) query.append("&");
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                 .append("=")
                 .append(URLEncoder.encode(signedParams.get(key), StandardCharsets.UTF_8));
        }

        String checkHash = hmacSHA512(query.toString(), hashSecret);
        return MessageDigest.isEqual(checkHash.getBytes(StandardCharsets.US_ASCII),
                vnp_SecureHash.getBytes(StandardCharsets.US_ASCII));
    }

    private String hmacSHA512(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append("0");
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to calculate VNPay HMAC", e);
        }
    }
}
