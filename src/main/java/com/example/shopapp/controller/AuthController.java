package com.example.shopapp.controller;

import com.example.shopapp.dto.auth.*;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request,
                                           HttpServletRequest httpServletRequest) {
        authService.register(
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok("✅ OTP sent to email");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody OtpRequest request) {
        authService.verifyOtp(request);
        return ResponseEntity.ok("✅ Email verified");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authService.login(
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request,
                                                 HttpServletRequest httpServletRequest) {
        authService.forgotPassword(
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok("✅ Password reset token sent");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request,
                                                HttpServletRequest httpServletRequest) {
        authService.resetPassword(
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok("✅ Password has been reset");
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(@RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        return ResponseEntity.ok(authService.getUserProfile(token));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpServletRequest) {
        String token = extractBearerToken(authHeader);
        return ResponseEntity.ok(authService.updateUserProfile(
                token,
                request,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")));
    }

    @GetMapping("/google/link-url")
    public ResponseEntity<GoogleLinkUrlResponse> getGoogleLinkUrl(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        return ResponseEntity.ok(authService.getGoogleLinkUrl(token));
    }

    @GetMapping("/google/status")
    public ResponseEntity<GoogleLinkStatusResponse> getGoogleLinkStatus(
            @RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        return ResponseEntity.ok(authService.getGoogleLinkStatus(token));
    }

    @GetMapping("/google/callback")
    public ResponseEntity<String> handleGoogleCallback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        try {
            authService.handleGoogleCallback(state, code, error);
            String successHtml = """
                    <html><body><script>
                    if (window.opener) {
                      window.opener.postMessage({ type: 'google-link-result', success: true }, '*');
                      window.close();
                    }
                    </script><h3>Liên kết Google thành công. Bạn có thể đóng cửa sổ này.</h3></body></html>
                    """;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body(successHtml);
        } catch (Exception ex) {
            String escapedMessage = ex.getMessage() == null ? "Liên kết Google thất bại" : ex.getMessage().replace("'", "");
            String errorHtml = """
                    <html><body><script>
                    if (window.opener) {
                      window.opener.postMessage({ type: 'google-link-result', success: false, message: '%s' }, '*');
                    }
                    </script><h3>Liên kết Google thất bại: %s</h3></body></html>
                    """.formatted(escapedMessage, escapedMessage);
            return ResponseEntity.badRequest()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body(errorHtml);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader,
                                         HttpServletRequest httpServletRequest) {
        String token = extractBearerToken(authHeader);
        authService.logout(
                token,
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent"));
        return ResponseEntity.ok("✅ Logged out successfully");
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new BadRequestException("Thiếu Authorization header");
        }

        if (!authHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Authorization header phải có dạng Bearer <token>");
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new BadRequestException("Token không được để trống");
        }

        return token;
    }
}