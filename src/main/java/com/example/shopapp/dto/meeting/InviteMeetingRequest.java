package com.example.shopapp.dto.meeting;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InviteMeetingRequest {

    @NotEmpty(message = "Danh sách email người được mời không được để trống")
    private List<@Email(message = "Email người tham gia không hợp lệ") String> attendeeEmails;
}
