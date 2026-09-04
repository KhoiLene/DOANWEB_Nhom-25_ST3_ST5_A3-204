package com.shopchain.service;

import com.shopchain.dto.RegisterRequest;
import com.shopchain.dto.AuthResponse;
import java.util.Optional;

public interface AuthService {
    String register(RegisterRequest request);
    String verifyOtp(String email, String otp);
    AuthResponse login(String username, String password);
    String forgotPassword(String email);
    String resetPassword(String email, String otp, String newPassword);
    void logout();

    // Thêm phương thức gửi OTP độc lập cho JS
    String sendRegistrationOtp(String email);
    String sendForgotPasswordOtp(String email);
}
