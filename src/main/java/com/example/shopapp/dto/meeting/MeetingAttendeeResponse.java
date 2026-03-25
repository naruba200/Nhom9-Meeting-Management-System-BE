package com.example.shopapp.dto.meeting;

import com.example.shopapp.enums.InvitationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MeetingAttendeeResponse {
    private Long id;
    private String email;
    private InvitationStatus status;
    private String responseReason;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
}
