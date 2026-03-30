package com.example.shopapp.dto.minutes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MinutesSignatureResponse {
    private Long id;
    private String signerEmail;
    private String signerName;
    private LocalDateTime signedAt;
    private boolean agreed;
    private String notes;
    private LocalDateTime createdAt;
}
