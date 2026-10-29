package com.shopchain.service.impl;

import com.shopchain.service.IntegrationConfigurationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {
    @Test
    void paymentCreationAndCallbackVerificationUseTheSameEffectiveDatabaseSecret() {
        IntegrationConfigurationService config = mock(IntegrationConfigurationService.class);
        when(config.vnPay()).thenReturn(new IntegrationConfigurationService.VnPaySettings(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "https://shop.example.test/api/payment/vnpay-return",
                "MERCHANT1", "test-hash-secret"));
        PaymentServiceImpl payment = new PaymentServiceImpl(config);

        String url = payment.createVnPayPaymentUrl(123L, new BigDecimal("100.00"), "127.0.0.1",
                LocalDateTime.now().plusMinutes(10));
        // Recreate the callback MAC over all received parameters, as VNPay does.
        String signedUrl = payment.createVnPayPaymentUrl(123L, new BigDecimal("100.00"), "127.0.0.1",
                LocalDateTime.now().plusMinutes(10));
        Map<String, String> signed = Arrays.stream(signedUrl.substring(signedUrl.indexOf('?') + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
        signed.put("vnp_ResponseCode", "00");
        signed.put("vnp_TransactionStatus", "00");
        signed.put("vnp_TransactionNo", "777");
        String hash = hmacForTest(signed, "test-hash-secret");
        signed.put("vnp_SecureHash", hash);

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(signed.get("vnp_TmnCode")).isEqualTo("MERCHANT1");
        assertThat(signed.get("vnp_ReturnUrl")).isEqualTo("https://shop.example.test/api/payment/vnpay-return");
        assertThat(payment.verifyVnPayPayment(signed)).isTrue();
        signed.put("vnp_TmnCode", "OTHER");
        assertThat(payment.verifyVnPayPayment(signed)).isFalse();
    }

    private String hmacForTest(Map<String, String> params, String secret) {
        try {
            var keys = params.keySet().stream().filter(key -> !key.equals("vnp_SecureHash"))
                    .filter(key -> !key.equals("vnp_SecureHashType")).sorted().toList();
            String payload = keys.stream().map(key -> URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(params.get(key), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            var mac = javax.crypto.Mac.getInstance("HmacSHA512");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
