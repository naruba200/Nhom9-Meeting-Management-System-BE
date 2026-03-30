package com.example.shopapp.dto.meeting;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeetingAgendaItemResponse {

    private Long id;
    private String title;
    private Integer durationMinutes;
    private String description;
    private Integer itemOrder;
}
