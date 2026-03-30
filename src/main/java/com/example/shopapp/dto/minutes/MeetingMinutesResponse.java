package com.example.shopapp.dto.minutes;

import com.example.shopapp.enums.MinutesStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesResponse {
    private Long id;
    private Long meetingId;
    private String title;
    private String location;
    private String purpose;
    private String attendees;
    private String absentees;
    private String content;
    private String decisions;
    private String contributions;
    private String voting;
    private String conclusions;
    private LocalDateTime minutesCreatedAt;
    private LocalDateTime minutesClosedAt;
    private MinutesStatus status;
    private List<MinutesSignatureResponse> signatures;
    private List<MinutesTaskResponse> tasks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String pdfUrl;
}
