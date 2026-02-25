package com.example.shopapp.controller;

import com.example.shopapp.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestEmailController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/send")
    public String testSendEmail() {
        String to = "nguyengiangsun@gmail.com"; // Thay bằng email bạn muốn gửi thử
        String subject = "✅ Kiểm tra gửi mail từ Spring Boot";
        String body = "Chào bạn,\n\nĐây là email test được gửi từ ứng dụng Spring Boot.\n\nThân mến.";
        emailService.sendEmail(to, subject, body);
        return "Email sent (nếu không lỗi SMTP)!";
    }
}