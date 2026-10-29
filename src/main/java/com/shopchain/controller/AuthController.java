package com.shopchain.controller;

import com.shopchain.service.AuthService;
import com.shopchain.dto.AuthRequest;
import com.shopchain.dto.AuthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

import java.util.Map;

/**
 * REST Controller xử lý xác thực tài khoản và bảo mật người dùng.
 * Cung cấp các endpoint: đăng nhập, gửi OTP qua email, xác minh OTP, đặt lại mật khẩu và đăng xuất.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Xác thực thông tin đăng nhập và cấp token phiên làm việc.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            return ResponseEntity.ok(authService.login(request.getUsername(), request.getPassword()));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Tên đăng nhập hoặc mật khẩu không đúng."));
        }
    }

    /**
     * Gửi mã OTP về email người dùng phục vụ đăng ký hoặc quên mật khẩu.
     */
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestParam String email, @RequestParam String type) {
        try {
            String message;
            if ("registration".equals(type)) {
                message = authService.sendRegistrationOtp(email);
            } else if ("forgot-password".equals(type)) {
                message = authService.sendForgotPasswordOtp(email);
            } else {
                return ResponseEntity.badRequest().body("Invalid OTP type");
            }
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Xác thực tính hợp lệ của mã OTP gửi qua email.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        try {
            String message = authService.verifyOtp(email, otp);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Đặt lại mật khẩu mới sau khi xác thực OTP thành công.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestParam String email,
                                           @RequestParam String otp,
                                           @RequestParam String newPassword) {
        try {
            String message = authService.resetPassword(email, otp, newPassword);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Hủy phiên đăng nhập của người dùng hiện tại.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) authService.logout(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
