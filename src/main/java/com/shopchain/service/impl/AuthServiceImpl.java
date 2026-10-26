package com.shopchain.service.impl;

import com.shopchain.dto.*;
import com.shopchain.entity.*;
import com.shopchain.repository.*;
import com.shopchain.security.JwtTokenProvider;
import com.shopchain.service.AuthService;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder, JavaMailSender mailSender,
                           JwtTokenProvider tokenProvider, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    // Tạm thời dùng Map để lưu OTP (Trong thực tế nên dùng Redis)
    private final Map<String, String> otpCache = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists!");
        }

        // 1. Mã hóa mật khẩu
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 2. Tạo user (chưa enable)
        User user = User.builder()
                .username(request.getUsername())
                .password(encodedPassword)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .enabled(false)
                .build();

        // Gán role mặc định là ROLE_USER
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));
        user.setRole(userRole);

        userRepository.save(user);

        // 3. Gửi OTP qua Email
        String otp = generateOtp();
        otpCache.put(user.getEmail(), otp);
        sendOtpEmail(user.getEmail(), otp);

        return "Registration successful! Please check your email for OTP activation.";
    }

    @Override
    @Transactional
    public String verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!otp.equals(otpCache.get(email))) {
            throw new RuntimeException("Invalid OTP code!");
        }

        user.setEnabled(true);
        userRepository.save(user);
        otpCache.remove(email);
        return "Account activated successfully!";
    }

    @Override
    public AuthResponse login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        String jwt = tokenProvider.generateToken(authentication);
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow();

        return AuthResponse.builder()
                .accessToken(jwt)
                .username(user.getUsername())
                .role(user.getRole().getName())
                .build();
    }

    @Override
    public String forgotPassword(String email) {
        return sendForgotPasswordOtp(email);
    }

    @Override
    public String sendForgotPasswordOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not registered!"));

        String otp = generateOtp();
        otpCache.put(email, "FORGOT_" + otp);
        sendOtpEmail(email, otp);
        return "OTP for password reset sent to your email.";
    }

    @Override
    @Transactional
    public String resetPassword(String email, String otp, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!("FORGOT_" + otp).equals(otpCache.get(email))) {
            throw new RuntimeException("Invalid OTP code!");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        otpCache.remove(email);
        return "Password reset successfully!";
    }

    @Override
    public String sendRegistrationOtp(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered!");
        }
        String otp = generateOtp();
        otpCache.put(email, otp);
        sendOtpEmail(email, otp);
        return "OTP sent to your email for registration.";
    }

    @Override
    public void logout() {
    }

    private String generateOtp() {
        return String.valueOf(new Random().nextInt(899999) + 100000);
    }

    private void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Shop Chain - Verification Code");
        message.setText("Your verification code is: " + otp + ". This code expires in 5 minutes.");
        mailSender.send(message);
    }
}
