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
public class MinutesTaskResponse {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private String assigneeEmail;
    private LocalDateTime createdAt;
}
