package com.example.shopapp.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GoogleLinkUrlResponse {
    private String authorizationUrl;
}
