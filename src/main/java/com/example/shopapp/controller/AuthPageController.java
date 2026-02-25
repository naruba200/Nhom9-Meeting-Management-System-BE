package com.example.shopapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.shopapp.dto.auth.OtpRequest;

@Controller
public class AuthPageController {

    @GetMapping("/register")
    public String showRegisterForm() {
        return "register";
    }

    @GetMapping("/verify-otp")
    public String showVerifyOtpPage(@RequestParam("email") String email, Model model) {
        OtpRequest otpRequest = new OtpRequest();
        otpRequest.setEmail(email);
        model.addAttribute("otpRequest", otpRequest);
        return "verify-otp";
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm() {
        return "reset-password";
    }

    @GetMapping("/")
    public String home() {
        return "home"; // Tên file home.html trong templates/
    }
}