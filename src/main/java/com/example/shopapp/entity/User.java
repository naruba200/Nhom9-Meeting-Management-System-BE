package com.example.shopapp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String password;

    private boolean enabled = false; // Được bật sau khi xác minh OTP

    private String role = "USER"; // Hoặc "ADMIN"

    @Column(length = 150)
    private String googleAccountEmail;

    @Column(length = 3000)
    private String googleAccessToken;

    @Column(length = 3000)
    private String googleRefreshToken;

    private LocalDateTime googleTokenExpiryAt;

    @Column(nullable = false)
    private boolean googleCalendarLinked = false;

    @Column(length = 150)
    private String googleOauthState;

    private LocalDateTime googleOauthStateExpiresAt;
}