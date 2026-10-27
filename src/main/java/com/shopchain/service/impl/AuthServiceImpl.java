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
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
        if (request == null) throw new IllegalArgumentException("Thông tin đăng ký chưa hợp lệ.");
        String username = request.getUsername() == null ? "" : request.getUsername().trim().toLowerCase(Locale.ROOT);
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
        String phone = request.getPhone() == null ? "" : request.getPhone().replaceAll("[\\s()\\-]", "");
        String password = request.getPassword();

        if (!username.matches("[a-z0-9._-]{4,30}"))
            throw new IllegalArgumentException("Tên đăng nhập cần từ 4 đến 30 ký tự: chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang.");
        if (userRepository.findByUsernameIgnoreCase(username).isPresent())
            throw new IllegalArgumentException("Tên đăng nhập này đã được sử dụng.");
        if (email.length() > 254 || !email.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$"))
            throw new IllegalArgumentException("Địa chỉ email chưa đúng định dạng.");
        if (userRepository.findByEmailIgnoreCase(email).isPresent())
            throw new IllegalArgumentException("Email này đã được đăng ký.");
        if (fullName.isBlank() || fullName.length() > 100)
            throw new IllegalArgumentException("Họ tên cần có từ 1 đến 100 ký tự.");
        if (!phone.matches("\\+?[0-9]{9,15}"))
            throw new IllegalArgumentException("Số điện thoại cần có từ 9 đến 15 chữ số; có thể bắt đầu bằng +84.");
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Mật khẩu cần từ 8 ký tự và tối đa 72 byte.");

        String encodedPassword = passwordEncoder.encode(password);

        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .enabled(true)
                .build();

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_USER").build()));
        user.setRole(userRole);
        userRepository.save(user);
        return "Tài khoản khách hàng đã được tạo. Bạn có thể đăng nhập ngay.";
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
                .or(() -> userRepository.findByEmailIgnoreCase(username))
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
