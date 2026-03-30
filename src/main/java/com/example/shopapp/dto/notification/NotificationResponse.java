package com.example.shopapp.dto.notification;

import com.example.shopapp.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean read;
    private Long meetingId;
    private LocalDateTime createdAt;
}
