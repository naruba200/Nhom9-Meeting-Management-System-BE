package com.example.shopapp.service;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.auth.RegisterRequest;
import com.example.shopapp.entity.OtpToken;
import com.example.shopapp.entity.User;
import com.example.shopapp.repository.*;
import com.example.shopapp.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for AuthService - Login and Register operations
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests - Login & Register")
class AuthServiceLoginRegisterTest {

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

    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .fullName("Test User")
                .password("encodedPassword")
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register user successfully with valid data")
        void register_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("newuser@example.com");
            request.setFullName("New User");
            request.setPassword("SecurePass123");
            request.setPhone("0901234567");

            given(userRepository.existsByEmail("newuser@example.com")).willReturn(false);
            given(passwordEncoder.encode("SecurePass123")).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willReturn(new User());
            given(otpTokenRepository.save(any(OtpToken.class))).willReturn(new OtpToken());
            willDoNothing().given(emailService).sendEmail(anyString(), anyString(), anyString());

            // Act
            String result = authService.register(request);

            // Assert
            assertThat(result).contains("Đăng ký thành công");
            then(userRepository).should().save(any(User.class));
            then(emailService).should().sendEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void register_EmailExists_ThrowsException() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("existing@example.com");
            request.setFullName("Existing User");
            request.setPassword("SecurePass123");
            request.setPhone("0901234567");

            given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

            // Act & Assert
            RuntimeException exception = catchThrowableOfType(
                    () -> authService.register(request),
                    RuntimeException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Email đã được đăng ký");
            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should save user with encoded password")
        void register_PasswordEncoded_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("newuser@example.com");
            request.setFullName("New User");
            request.setPassword("SecurePass123");
            request.setPhone("0901234567");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            given(userRepository.existsByEmail("newuser@example.com")).willReturn(false);
            given(passwordEncoder.encode("SecurePass123")).willReturn("encodedPassword");
            given(userRepository.save(userCaptor.capture())).willReturn(new User());
            given(otpTokenRepository.save(any(OtpToken.class))).willReturn(new OtpToken());
            willDoNothing().given(emailService).sendEmail(anyString(), anyString(), anyString());

            // Act
            authService.register(request);

            // Assert
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
            assertThat(savedUser.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should save user as disabled until OTP verified")
        void register_UserDisabledUntilOtp_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("newuser@example.com");
            request.setFullName("New User");
            request.setPassword("SecurePass123");
            request.setPhone("0901234567");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            given(userRepository.existsByEmail("newuser@example.com")).willReturn(false);
            given(passwordEncoder.encode("SecurePass123")).willReturn("encodedPassword");
            given(userRepository.save(userCaptor.capture())).willReturn(new User());
            given(otpTokenRepository.save(any(OtpToken.class))).willReturn(new OtpToken());
            willDoNothing().given(emailService).sendEmail(anyString(), anyString(), anyString());

            // Act
            authService.register(request);

            // Assert
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void login_Success() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("SecurePass123");

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("SecurePass123", "encodedPassword")).willReturn(true);
            given(jwtUtil.generateToken("test@example.com", "USER")).willReturn("jwt-token-123");
            willDoNothing().given(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any(), anyInt());

            // Act
            AuthResponse response = authService.login(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo("jwt-token-123");
            assertThat(response.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("Should throw exception when email not found")
        void login_EmailNotFound_ThrowsException() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("notfound@example.com");
            request.setPassword("SecurePass123");

            given(userRepository.findByEmail("notfound@example.com")).willReturn(Optional.empty());

            // Act & Assert
            RuntimeException exception = catchThrowableOfType(
                    () -> authService.login(request),
                    RuntimeException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Email chưa đăng ký");
        }

        @Test
        @DisplayName("Should throw exception when account not enabled")
        void login_AccountNotEnabled_ThrowsException() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("SecurePass123");

            User disabledUser = User.builder()
                    .email("test@example.com")
                    .fullName("Test User")
                    .password("encodedPassword")
                    .enabled(false)
                    .build();

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(disabledUser));

            // Act & Assert
            RuntimeException exception = catchThrowableOfType(
                    () -> authService.login(request),
                    RuntimeException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Tài khoản chưa được xác minh OTP");
        }

        @Test
        @DisplayName("Should throw exception when password incorrect")
        void login_WrongPassword_ThrowsException() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("WrongPassword");

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("WrongPassword", "encodedPassword")).willReturn(false);

            // Act & Assert
            RuntimeException exception = catchThrowableOfType(
                    () -> authService.login(request),
                    RuntimeException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Mật khẩu không chính xác");
        }

        @Test
        @DisplayName("Should generate JWT token on successful login")
        void login_GenerateToken_Success() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("SecurePass123");

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> roleCaptor = ArgumentCaptor.forClass(String.class);

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("SecurePass123", "encodedPassword")).willReturn(true);
            given(jwtUtil.generateToken(emailCaptor.capture(), roleCaptor.capture())).willReturn("jwt-token-123");
            willDoNothing().given(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any(), anyInt());

            // Act
            AuthResponse response = authService.login(request);

            // Assert
            assertThat(emailCaptor.getValue()).isEqualTo("test@example.com");
            assertThat(roleCaptor.getValue()).isEqualTo("USER");
        }

        @Test
        @DisplayName("Should return user role in response")
        void login_ReturnUserRole_Success() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("admin@example.com");
            request.setPassword("SecurePass123");

            User adminUser = User.builder()
                    .email("admin@example.com")
                    .fullName("Admin User")
                    .password("encodedPassword")
                    .enabled(true)
                    .role("ADMIN")
                    .build();

            given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(adminUser));
            given(passwordEncoder.matches("SecurePass123", "encodedPassword")).willReturn(true);
            given(jwtUtil.generateToken("admin@example.com", "ADMIN")).willReturn("admin-jwt-token");
            willDoNothing().given(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any(), anyInt());

            // Act
            AuthResponse response = authService.login(request);

            // Assert
            assertThat(response.getRole()).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("Should handle login with IP address and user agent")
        void login_WithIpAddressAndUserAgent_Success() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("SecurePass123");

            String ipAddress = "192.168.1.1";
            String userAgent = "Mozilla/5.0";

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("SecurePass123", "encodedPassword")).willReturn(true);
            given(jwtUtil.generateToken("test@example.com", "USER")).willReturn("jwt-token-123");
            willDoNothing().given(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any(), anyInt());

            // Act
            AuthResponse response = authService.login(request, ipAddress, userAgent);

            // Assert
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle multiple login attempts")
        void multipleLoginAttempts_Success() {
            // Arrange
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("SecurePass123");

            given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(testUser));
            given(passwordEncoder.matches("SecurePass123", "encodedPassword")).willReturn(true);
            given(jwtUtil.generateToken(anyString(), anyString())).willReturn("new-token");
            willDoNothing().given(activityLogService).logActivity(any(), any(), any(), any(), any(), any(), any(), anyInt());

            // Act
            authService.login(request);
            authService.login(request);

            // Assert
            verify(userRepository, times(2)).findByEmail("test@example.com");
        }

        @Test
        @DisplayName("Should handle registration with special characters in name")
        void register_SpecialCharactersInName_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("special@example.com");
            request.setFullName("Nguyễn Văn A");
            request.setPassword("SecurePass123");
            request.setPhone("0901234567");

            given(userRepository.existsByEmail("special@example.com")).willReturn(false);
            given(passwordEncoder.encode("SecurePass123")).willReturn("encodedPassword");
            given(userRepository.save(any(User.class))).willReturn(new User());
            given(otpTokenRepository.save(any(OtpToken.class))).willReturn(new OtpToken());
            willDoNothing().given(emailService).sendEmail(anyString(), anyString(), anyString());

            // Act
            String result = authService.register(request);

            // Assert
            assertThat(result).contains("Đăng ký thành công");
        }

        @Test
        @DisplayName("Should capture OTP token creation correctly")
        void register_SendOtp_CapturesToken_Success() {
            // Arrange
            RegisterRequest request = new RegisterRequest();
            request.setEmail("newuser@example.com");
            request.setFullName("New User");
            request.setPassword("SecurePass123");
            request.setPhone("0901234567");

            ArgumentCaptor<OtpToken> otpCaptor = ArgumentCaptor.forClass(OtpToken.class);
            given(userRepository.existsByEmail("newuser@example.com")).willReturn(false);
            given(passwordEncoder.encode("SecurePass123")).willReturn("encodedPassword");
            given(otpTokenRepository.save(otpCaptor.capture())).willReturn(new OtpToken());
            given(userRepository.save(any(User.class))).willReturn(new User());
            willDoNothing().given(emailService).sendEmail(anyString(), anyString(), anyString());

            // Act
            authService.register(request);

            // Assert
            OtpToken savedOtp = otpCaptor.getValue();
            assertThat(savedOtp.getEmail()).isEqualTo("newuser@example.com");
            assertThat(savedOtp.getOtp()).hasSize(6);
            assertThat(savedOtp.isUsed()).isFalse();
        }
    }
}
