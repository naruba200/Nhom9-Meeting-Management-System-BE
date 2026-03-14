package com.example.shopapp.service;

import com.example.shopapp.dto.auth.*;
import com.example.shopapp.entity.*;
import com.example.shopapp.repository.*;
import com.example.shopapp.util.JwtUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Đăng ký
    public String register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được đăng ký");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .enabled(false) // Chưa xác minh OTP
                .build();

        userRepository.save(user);
        sendOtp(request.getEmail()); // Gửi OTP

        return "Đăng ký thành công. Vui lòng xác minh email với mã OTP.";
    }

    // Gửi OTP
    public void sendOtp(String email) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        OtpToken otpToken = OtpToken.builder()
                .email(email)
                .otp(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();
        otpTokenRepository.save(otpToken);

        // ✅ Gửi mail
        String subject = "🔐 MÁY CỦA BẠN ĐÃ BỊ HACK";
        String body = "Đã lấy hết dữ liệu,\n\nMã ASVM của bạn là: " + otp
                + "\nASVM có hiệu lực trong 5 phút.\n\nThân mến.";
        emailService.sendEmail(email, subject, body);
    }

    // Xác minh OTP
    @Transactional
    public String verifyOtp(OtpRequest request) {
        Optional<OtpToken> latestOtpOpt = otpTokenRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(request.getEmail());

        if (latestOtpOpt.isEmpty())
            throw new RuntimeException("Không tìm thấy mã OTP");

        OtpToken otp = latestOtpOpt.get();
        if (otp.isUsed() || otp.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Mã OTP đã hết hạn hoặc đã dùng");

        if (!otp.getOtp().equals(request.getOtp()))
            throw new RuntimeException("Mã OTP không chính xác");

        otp.setUsed(true);
        otpTokenRepository.save(otp);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        user.setEnabled(true);
        userRepository.save(user);

        return "Xác minh OTP thành công. Bạn có thể đăng nhập.";
    }

    // Đăng nhập
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email chưa đăng ký"));

        if (!user.isEnabled())
            throw new RuntimeException("Tài khoản chưa được xác minh OTP");

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new RuntimeException("Mật khẩu không chính xác");

        String token = jwtUtil.generateToken(user.getEmail());

        return new AuthResponse(token, user.getEmail(), user.getFullName());
    }

    // Gửi token quên mật khẩu
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Email không tồn tại"));

        String token = jwtUtil.generateToken(user.getEmail());

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(user.getEmail())
                .token(token)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);

        // TODO: Gửi token qua email
        emailService.sendEmail(
                user.getEmail(),
                "🔐 Token đặt lại mật khẩu",
                "Token đặt lại mật khẩu của bạn là:\n\n" + token + "\n\nToken sẽ hết hạn sau 10 phút.");

        return "Đã gửi token đặt lại mật khẩu qua email.";
    }

    // Đặt lại mật khẩu
    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Token không hợp lệ"));

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Token đã hết hạn hoặc đã dùng");

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return "Đặt lại mật khẩu thành công.";
    }

    // Lấy thông tin người dùng từ token
    public UserProfileResponse getUserProfile(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("Token không hợp lệ");
        }

        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }

    // Cập nhật thông tin người dùng
    @Transactional
    public UserProfileResponse updateUserProfile(String token, UpdateProfileRequest request) {
        if (!jwtUtil.validateToken(token)) {
            throw new RuntimeException("Token không hợp lệ");
        }

        String email = jwtUtil.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (request.getFullName() != null && !request.getFullName().isEmpty()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        userRepository.save(user);

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .build();
    }
}