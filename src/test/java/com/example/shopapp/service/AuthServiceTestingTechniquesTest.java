package com.example.shopapp.service;

import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.auth.OtpRequest;
import com.example.shopapp.dto.auth.RegisterRequest;
import com.example.shopapp.dto.auth.UpdateProfileRequest;
import com.example.shopapp.entity.OtpToken;
import com.example.shopapp.entity.User;
import com.example.shopapp.repository.OtpTokenRepository;
import com.example.shopapp.repository.PasswordResetTokenRepository;
import com.example.shopapp.repository.UserRepository;
import com.example.shopapp.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTestingTechniquesTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpTokenRepository otpTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GoogleOAuthService googleOAuthService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Black-box: Equivalence partitioning - register succeeds for a new email")
    void register_equivalencePartition_newEmail_success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new-user@example.com");
        request.setFullName("New User");
        request.setPassword("123456");
        request.setPhone("0123456789");

        when(userRepository.existsByEmail("new-user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("123456")).thenReturn("encoded");

        assertDoesNotThrow(() -> authService.register(request));

        verify(userRepository).save(any(User.class));
        verify(otpTokenRepository).save(any(OtpToken.class));
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Black-box: Decision table - login denied when account is not enabled")
    void login_decisionTable_enabledFalse_denied() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret");

        User user = User.builder()
                .email("user@example.com")
                .password("encoded")
                .enabled(false)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class, () -> authService.login(request));
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("White-box: Statement coverage - verifyOtp happy path executes all key statements")
    void verifyOtp_statementCoverage_happyPath() {
        OtpRequest request = new OtpRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");

        OtpToken otp = OtpToken.builder()
                .email("user@example.com")
                .otp("123456")
                .used(false)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .build();

        User user = User.builder()
                .email("user@example.com")
                .enabled(false)
                .password("encoded")
                .build();

        when(otpTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(Optional.of(otp));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        String result = authService.verifyOtp(request);

        assertNotNull(result);
        verify(otpTokenRepository).save(any(OtpToken.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("White-box: Branch coverage - verifyOtp throws for expired OTP branch")
    void verifyOtp_branchCoverage_expiredToken_throws() {
        OtpRequest request = new OtpRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");

        OtpToken expiredOtp = OtpToken.builder()
                .email("user@example.com")
                .otp("123456")
                .used(false)
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(otpTokenRepository.findTopByEmailAndUsedFalseOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(Optional.of(expiredOtp));

        assertThrows(RuntimeException.class, () -> authService.verifyOtp(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("White-box: Condition coverage - update profile checks fullName and phone conditions")
    void updateProfile_conditionCoverage_nullFullName_nonNullPhone_updatesPhoneOnly() {
        String token = "valid-token";
        UpdateProfileRequest request = new UpdateProfileRequest(null, "0988888888");

        User user = User.builder()
                .email("user@example.com")
                .fullName("Old Name")
                .phone("0123")
                .enabled(true)
                .password("encoded")
                .build();

        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.getEmailFromToken(token)).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> authService.updateUserProfile(token, request));

        verify(userRepository).save(any(User.class));
    }
}
