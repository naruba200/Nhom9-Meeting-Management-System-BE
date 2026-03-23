package com.example.shopapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {
    private String fullName;
    private String phone;
    private String role;
    private boolean enabled;
}
