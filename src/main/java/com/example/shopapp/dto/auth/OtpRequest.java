package com.example.shopapp.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpRequest {
    private String email;
    private String otp;
}