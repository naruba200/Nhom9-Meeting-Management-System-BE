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
    private String room;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String organizerEmail;
    private MeetingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
