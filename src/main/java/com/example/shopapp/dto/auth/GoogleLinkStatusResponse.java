package com.example.shopapp.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class GoogleLinkStatusResponse {
    private boolean linked;
    private String googleAccountEmail;
    private LocalDateTime tokenExpiryAt;
}
