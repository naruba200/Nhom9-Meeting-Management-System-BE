package com.example.shopapp.service;

import com.example.shopapp.entity.User;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for Google OAuth - Linking account with vi.duong059@gmail.com
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleOAuthService Unit Tests - Link Account vi.duong059@gmail.com")
class GoogleOAuthLinkAccountTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    private User testUser;
    private String testState;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("vi.duong059@gmail.com")
                .fullName("Vi Duong")
                .password("encodedPassword")
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();

        testState = UUID.randomUUID().toString();

        // Set up OAuth config
        ReflectionTestUtils.setField(googleOAuthService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(googleOAuthService, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(googleOAuthService, "redirectUri", "http://localhost:8080/api/auth/google/callback");
    }

    @Nested
    @DisplayName("Generate Authorization URL Tests")
    class GenerateAuthorizationUrlTests {

        @Test
        @DisplayName("Should generate authorization URL for vi.duong059@gmail.com")
        void generateAuthorizationUrl_Success() {
            // Arrange
            given(userRepository.save(any(User.class))).willReturn(testUser);

            // Act
            String authorizationUrl = googleOAuthService.generateAuthorizationUrl(testUser);

            // Assert
            assertThat(authorizationUrl).isNotNull();
            assertThat(authorizationUrl).contains("https://accounts.google.com/o/oauth2/v2/auth");
            assertThat(authorizationUrl).contains("client_id=test-client-id");
            assertThat(authorizationUrl).contains("redirect_uri=");
            then(userRepository).should().save(testUser);
        }

        @Test
        @DisplayName("Should save state and expiry time for user")
        void generateAuthorizationUrl_SavesState_Success() {
            // Arrange
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            given(userRepository.save(userCaptor.capture())).willReturn(testUser);

            // Act
            googleOAuthService.generateAuthorizationUrl(testUser);

            // Assert
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getGoogleOauthState()).isNotNull();
            assertThat(savedUser.getGoogleOauthStateExpiresAt()).isNotNull();
            assertThat(savedUser.getGoogleOauthStateExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("Should throw exception when client ID is not configured")
        void generateAuthorizationUrl_NoClientId_ThrowsException() {
            // Arrange
            ReflectionTestUtils.setField(googleOAuthService, "clientId", null);

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.generateAuthorizationUrl(testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("google.oauth.client-id");
        }

        @Test
        @DisplayName("Should throw exception when client secret is not configured")
        void generateAuthorizationUrl_NoClientSecret_ThrowsException() {
            // Arrange
            ReflectionTestUtils.setField(googleOAuthService, "clientSecret", null);

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.generateAuthorizationUrl(testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("google.oauth.client-secret");
        }
    }

    @Nested
    @DisplayName("Handle OAuth Callback Tests")
    class HandleOAuthCallbackTests {

        @Test
        @DisplayName("Should throw exception when state is missing")
        void handleOAuthCallback_MissingState_ThrowsException() {
            // Arrange
            String code = "authorization-code-123";

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.handleOAuthCallback(null, code, null),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Thiếu state OAuth");
        }

        @Test
        @DisplayName("Should throw exception when code is missing")
        void handleOAuthCallback_MissingCode_ThrowsException() {
            // Arrange
            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.handleOAuthCallback(testState, null, null),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Thiếu mã xác thực từ Google");
        }

        @Test
        @DisplayName("Should throw exception when state is invalid")
        void handleOAuthCallback_InvalidState_ThrowsException() {
            // Arrange
            String code = "authorization-code-123";
            given(userRepository.findByGoogleOauthState(testState)).willReturn(Optional.empty());

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.handleOAuthCallback(testState, code, null),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("State OAuth không hợp lệ");
        }

        @Test
        @DisplayName("Should throw exception when state is expired")
        void handleOAuthCallback_ExpiredState_ThrowsException() {
            // Arrange
            String code = "authorization-code-123";
            testUser.setGoogleOauthState(testState);
            testUser.setGoogleOauthStateExpiresAt(LocalDateTime.now().minusMinutes(5)); // Expired

            given(userRepository.findByGoogleOauthState(testState)).willReturn(Optional.of(testUser));

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.handleOAuthCallback(testState, code, null),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("State OAuth đã hết hạn");
        }

        @Test
        @DisplayName("Should throw exception when Google rejects the link")
        void handleOAuthCallback_GoogleRejects_ThrowsException() {
            // Arrange
            String code = "authorization-code-123";
            String error = "access_denied";

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.handleOAuthCallback(testState, code, error),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Google từ chối liên kết");
        }
    }

    @Nested
    @DisplayName("Get Valid Access Token Tests")
    class GetValidAccessTokenTests {

        @Test
        @DisplayName("Should return access token when Google account is linked")
        void getValidAccessToken_Success() {
            // Arrange
            testUser.setGoogleCalendarLinked(true);
            testUser.setGoogleAccessToken("valid-access-token");
            testUser.setGoogleTokenExpiryAt(LocalDateTime.now().plusHours(1));

            // Act
            String accessToken = googleOAuthService.getValidAccessToken(testUser);

            // Assert
            assertThat(accessToken).isEqualTo("valid-access-token");
        }

        @Test
        @DisplayName("Should throw exception when Google account is not linked")
        void getValidAccessToken_NotLinked_ThrowsException() {
            // Arrange
            testUser.setGoogleCalendarLinked(false);

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.getValidAccessToken(testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Tài khoản chưa liên kết Google");
        }

        @Test
        @DisplayName("Should throw exception when access token is null")
        void getValidAccessToken_NullToken_ThrowsException() {
            // Arrange
            testUser.setGoogleCalendarLinked(true);
            testUser.setGoogleAccessToken(null);

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.getValidAccessToken(testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Không có Google access token");
        }

        @Test
        @DisplayName("Should throw exception when token is expired and no refresh token")
        void getValidAccessToken_Expired_NoRefreshToken_ThrowsException() {
            // Arrange
            testUser.setGoogleCalendarLinked(true);
            testUser.setGoogleAccessToken("expired-token");
            testUser.setGoogleTokenExpiryAt(LocalDateTime.now().minusMinutes(5));
            testUser.setGoogleRefreshToken(null);

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> googleOAuthService.getValidAccessToken(testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Google token đã hết hạn");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle user with existing Google account email")
        void handleExistingGoogleEmail_Success() {
            // Arrange
            testUser.setGoogleAccountEmail("vi.duong059@gmail.com");
            testUser.setGoogleOauthState(testState);
            testUser.setGoogleOauthStateExpiresAt(LocalDateTime.now().plusMinutes(10));

            given(userRepository.findByGoogleOauthState(testState)).willReturn(Optional.of(testUser));

            // Act - Will throw exception due to HTTP call
            assertThatThrownBy(() -> googleOAuthService.handleOAuthCallback(testState, "code", null))
                    .isInstanceOf(Exception.class);

            // Assert - User should be found and processed
            then(userRepository).should().findByGoogleOauthState(testState);
        }
    }
}
