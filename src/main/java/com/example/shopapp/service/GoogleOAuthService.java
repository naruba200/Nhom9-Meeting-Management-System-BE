package com.example.shopapp.service;

import com.example.shopapp.entity.User;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${google.oauth.client-id:}")
    private String clientId;

    @Value("${google.oauth.client-secret:}")
    private String clientSecret;

    @Value("${google.oauth.redirect-uri:http://localhost:8080/api/auth/google/callback}")
    private String redirectUri;

    @Value("${google.oauth.scope:https://www.googleapis.com/auth/calendar.events https://www.googleapis.com/auth/userinfo.email openid}")
    private String scope;

    public String generateAuthorizationUrl(User user) {
        ensureOAuthConfig();

        String state = UUID.randomUUID().toString();
        user.setGoogleOauthState(state);
        user.setGoogleOauthStateExpiresAt(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_URL)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
            .build()
            .encode()
                .toUriString();
    }

    public void handleOAuthCallback(String state, String code, String error) {
        if (error != null && !error.isBlank()) {
            throw new BadRequestException("Google từ chối liên kết: " + error);
        }

        if (state == null || state.isBlank()) {
            throw new BadRequestException("Thiếu state OAuth");
        }

        if (code == null || code.isBlank()) {
            throw new BadRequestException("Thiếu mã xác thực từ Google");
        }

        User user = userRepository.findByGoogleOauthState(state)
                .orElseThrow(() -> new BadRequestException("State OAuth không hợp lệ"));

        if (user.getGoogleOauthStateExpiresAt() == null || user.getGoogleOauthStateExpiresAt().isBefore(LocalDateTime.now())) {
            clearOauthState(user);
            throw new BadRequestException("State OAuth đã hết hạn, vui lòng thử liên kết lại");
        }

        GoogleTokenResult tokenResult = exchangeCodeForToken(code);
        String googleEmail = fetchGoogleEmail(tokenResult.accessToken());

        user.setGoogleAccessToken(tokenResult.accessToken());
        if (tokenResult.refreshToken() != null && !tokenResult.refreshToken().isBlank()) {
            user.setGoogleRefreshToken(tokenResult.refreshToken());
        }
        user.setGoogleTokenExpiryAt(LocalDateTime.now().plusSeconds(tokenResult.expiresIn()));
        user.setGoogleAccountEmail(googleEmail);
        user.setGoogleCalendarLinked(true);
        clearOauthState(user);
        userRepository.save(user);
    }

    public String getValidAccessToken(User user) {
        if (!user.isGoogleCalendarLinked()) {
            throw new BadRequestException("Tài khoản chưa liên kết Google. Vui lòng vào Cài đặt để liên kết.");
        }

        if (user.getGoogleAccessToken() == null || user.getGoogleAccessToken().isBlank()) {
            throw new BadRequestException("Không có Google access token. Vui lòng liên kết lại tài khoản Google.");
        }

        LocalDateTime expiryAt = user.getGoogleTokenExpiryAt();
        if (expiryAt == null || expiryAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            return user.getGoogleAccessToken();
        }

        if (user.getGoogleRefreshToken() == null || user.getGoogleRefreshToken().isBlank()) {
            throw new BadRequestException("Google token đã hết hạn và không có refresh token. Vui lòng liên kết lại tài khoản Google.");
        }

        GoogleTokenResult refreshed = refreshAccessToken(user.getGoogleRefreshToken());
        user.setGoogleAccessToken(refreshed.accessToken());
        user.setGoogleTokenExpiryAt(LocalDateTime.now().plusSeconds(refreshed.expiresIn()));
        userRepository.save(user);
        return refreshed.accessToken();
    }

    private GoogleTokenResult exchangeCodeForToken(String code) {
        ensureOAuthConfig();

        String body = formEncoded(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", redirectUri
        );

        return requestToken(body);
    }

    private GoogleTokenResult refreshAccessToken(String refreshToken) {
        ensureOAuthConfig();

        String body = formEncoded(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"
        );

        return requestToken(body);
    }

    private GoogleTokenResult requestToken(String formBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BadRequestException("Không thể lấy token từ Google (HTTP " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String accessToken = root.path("access_token").asText("");
            String refreshToken = root.path("refresh_token").asText("");
            long expiresIn = root.path("expires_in").asLong(3600);

            if (accessToken.isBlank()) {
                throw new BadRequestException("Google không trả về access token");
            }

            return new GoogleTokenResult(accessToken, refreshToken, expiresIn);
        } catch (IOException e) {
            throw new BadRequestException("Không đọc được phản hồi token từ Google");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Yêu cầu token Google bị gián đoạn");
        }
    }

    private String fetchGoogleEmail(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_USERINFO_URL))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                return "";
            }

            JsonNode root = objectMapper.readTree(response.body());
            return root.path("email").asText("").trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private void clearOauthState(User user) {
        user.setGoogleOauthState(null);
        user.setGoogleOauthStateExpiresAt(null);
    }

    private void ensureOAuthConfig() {
        if (clientId == null || clientId.isBlank()) {
            throw new BadRequestException("Thiếu cấu hình Google OAuth: google.oauth.client-id");
        }

        if (clientSecret == null || clientSecret.isBlank()) {
            throw new BadRequestException("Thiếu cấu hình Google OAuth: google.oauth.client-secret");
        }
    }

    private String formEncoded(String... keyValuePairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(keyValuePairs[i], StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(Optional.ofNullable(keyValuePairs[i + 1]).orElse(""), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private record GoogleTokenResult(String accessToken, String refreshToken, long expiresIn) {
    }
}
