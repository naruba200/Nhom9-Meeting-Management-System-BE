package com.example.shopapp.controller;

import com.example.shopapp.dto.auth.*;
import com.example.shopapp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthWebController {

    private final AuthService authService;

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                                 BindingResult bindingResult,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            authService.register(request);
            model.addAttribute("email", request.getEmail());
            return "redirect:/verify-otp?email=" + request.getEmail();
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @PostMapping("/verify-otp")
    public String handleVerifyOtp(OtpRequest request, Model model) {
        try {
            authService.verifyOtp(request);
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", request.getEmail());
            return "verify-otp";
        }
    }

    @PostMapping("/login")
    public String handleLogin(@Valid @ModelAttribute("loginRequest") LoginRequest request,
                              BindingResult bindingResult,
                              Model model) {
        if (bindingResult.hasErrors()) {
            return "login";
        }

        try {
            AuthResponse response = authService.login(request);
            model.addAttribute("message", "Đăng nhập thành công");
            model.addAttribute("token", response.getToken());
            model.addAttribute("email", response.getEmail());
            model.addAttribute("fullName", response.getFullName());
            return "login-success";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(ForgotPasswordRequest request, Model model) {
        try {
            authService.forgotPassword(request);
            model.addAttribute("message", "Đã gửi token đặt lại mật khẩu");
            return "forgot-password";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "forgot-password";
        }
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(ResetPasswordRequest request, Model model) {
        try {
            authService.resetPassword(request);
            model.addAttribute("message", "Đặt lại mật khẩu thành công");
            return "login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "reset-password";
        }
    }
}
