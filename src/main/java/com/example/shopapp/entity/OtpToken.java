package com.example.shopapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String otp;

    @Column(nullable = false)
    private String email;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private boolean used = false;
}