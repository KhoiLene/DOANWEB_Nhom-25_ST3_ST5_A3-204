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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AuthServiceImpl implements AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration OTP_SEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final String GENERIC_OTP_MESSAGE = "Nếu thông tin phù hợp, mã xác minh sẽ được gửi đến email đã đăng ký.";

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

    // In-memory challenges are temporary; production multi-instance deployments should use shared Redis storage.
    private final Map<String, OtpChallenge> otpCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> otpLastSentAt = new ConcurrentHashMap<>();

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
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(this::invalidOtpException);
        verifyAndConsumeOtp(normalizedEmail, otp, "registration");

        user.setEnabled(true);
        userRepository.save(user);
        return "Tài khoản đã được xác minh.";
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
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isEmpty()) return GENERIC_OTP_MESSAGE;
        sendOtpChallenge(normalizedEmail, "forgot-password");
        return GENERIC_OTP_MESSAGE;
    }

    @Override
    @Transactional
    public String resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (newPassword == null || newPassword.length() < 8
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu cần từ 8 ký tự và tối đa 72 byte.");
        }
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseThrow(this::invalidOtpException);
        verifyAndConsumeOtp(normalizedEmail, otp, "forgot-password");

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return "Mật khẩu đã được đặt lại. Vui lòng đăng nhập lại.";
    }

    @Override
    public String sendRegistrationOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isEmpty()) {
            sendOtpChallenge(normalizedEmail, "registration");
        }
        return GENERIC_OTP_MESSAGE;
    }

    @Override
    public void logout() {
    }

    private void sendOtpChallenge(String email, String type) {
        Instant now = Instant.now();
        pruneOtpEntries(now);
        AtomicBoolean allowed = new AtomicBoolean(false);
        otpLastSentAt.compute(email, (key, previous) -> {
            if (previous != null && previous.plus(OTP_SEND_COOLDOWN).isAfter(now)) return previous;
            allowed.set(true);
            return now;
        });
        if (!allowed.get()) return;

        String otp = generateOtp();
        otpCache.put(email, new OtpChallenge(otp, type, now.plus(OTP_TTL), 0));
        try {
            sendOtpEmail(email, otp);
        } catch (RuntimeException exception) {
            otpCache.remove(email);
            otpLastSentAt.remove(email, now);
            throw new IllegalStateException("Không thể gửi mã xác minh lúc này. Vui lòng thử lại sau.");
        }
    }

    private void verifyAndConsumeOtp(String email, String submittedOtp, String expectedType) {
        if (submittedOtp == null || !submittedOtp.matches("[0-9]{6}")) throw invalidOtpException();
        AtomicBoolean verified = new AtomicBoolean(false);
        Instant now = Instant.now();
        otpCache.compute(email, (key, challenge) -> {
            if (challenge == null || !expectedType.equals(challenge.type())
                    || !challenge.expiresAt().isAfter(now) || challenge.failedAttempts() >= OTP_MAX_ATTEMPTS) {
                return null;
            }
            boolean matches = MessageDigest.isEqual(challenge.code().getBytes(StandardCharsets.US_ASCII),
                    submittedOtp.getBytes(StandardCharsets.US_ASCII));
            if (matches) {
                verified.set(true);
                return null;
            }
            return new OtpChallenge(challenge.code(), challenge.type(), challenge.expiresAt(),
                    challenge.failedAttempts() + 1);
        });
        if (!verified.get()) throw invalidOtpException();
        otpLastSentAt.remove(email);
    }

    private void pruneOtpEntries(Instant now) {
        otpCache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Instant cutoff = now.minus(Duration.ofMinutes(10));
        otpLastSentAt.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        if (otpCache.size() > 10_000) otpCache.clear();
        if (otpLastSentAt.size() > 10_000) otpLastSentAt.clear();
    }

    private IllegalArgumentException invalidOtpException() {
        return new IllegalArgumentException("Mã xác minh không hợp lệ hoặc đã hết hạn.");
    }

    private String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$")) {
            throw new IllegalArgumentException("Địa chỉ email chưa đúng định dạng.");
        }
        return normalized;
    }

    private String generateOtp() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Shop Chain - Verification Code");
        message.setText("Mã xác minh của bạn là: " + otp + ". Mã có hiệu lực trong 5 phút và chỉ dùng một lần.");
        mailSender.send(message);
    }

    private record OtpChallenge(String code, String type, Instant expiresAt, int failedAttempts) { }
}
