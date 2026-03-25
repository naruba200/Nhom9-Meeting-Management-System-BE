package com.example.shopapp.dto.invitation;

import com.example.shopapp.enums.InvitationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InvitationResponse {
    private Long attendeeId;
    private Long meetingId;
    private String meetingTitle;
    private String organizerEmail;
    private LocalDateTime meetingStartTime;
    private LocalDateTime meetingEndTime;
    private InvitationStatus status;
    private String responseReason;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
}
