package com.example.shopapp.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private final String FROM_EMAIL = "nguyengiangsun@gmail.com";

    public void sendEmail(String to, String subject, String body) {
        doSendEmail(to, subject, body);
    }

    @Async
    public void sendEmailAsync(String to, String subject, String body) {
        doSendEmail(to, subject, body);
    }

    private void doSendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(FROM_EMAIL);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false); // false = plain text

            mailSender.send(message);
            System.out.println("✅ Gửi email thành công đến: " + to);
        } catch (MessagingException | RuntimeException e) {
            System.err.println("❌ Lỗi gửi email: " + e.getMessage());
        }
    }

    public void sendOtpEmail(String to, String otpCode) {
        String subject = "Xác thực tài khoản của bạn";
        String body = "Mã OTP của bạn là: " + otpCode + "\n\n"
                + "Vui lòng nhập mã này trong vòng 5 phút để xác minh email.";
        sendEmail(to, subject, body);
    }

    public void sendResetPasswordEmail(String to, String resetLink) {
        String subject = "Khôi phục mật khẩu tài khoản của bạn";
        String body = "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu.\n"
                + "Hãy nhấn vào đường link dưới đây để tạo mật khẩu mới:\n\n"
                + resetLink + "\n\n"
                + "Nếu bạn không yêu cầu, vui lòng bỏ qua email này.";
        sendEmail(to, subject, body);
    }
}