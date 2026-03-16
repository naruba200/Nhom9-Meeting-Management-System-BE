package com.example.shopapp.dto.meeting;

import com.example.shopapp.enums.MeetingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MeetingResponse {

    private Long id;
    private String title;
    private String agenda;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String organizerEmail;
    private String meetingLink;
    private String googleCalendarEventId;
    private boolean syncedWithGoogleCalendar;
    private MeetingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
