package com.example.shopapp.dto.minutes;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignMeetingMinutesRequest {

    @NotBlank(message = "Email không được để trống")
    private String signerEmail;

    private String signerName;

    private boolean agreed;

    private String notes;
}
