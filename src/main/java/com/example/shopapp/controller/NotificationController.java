package com.example.shopapp.controller;

import com.example.shopapp.dto.notification.NotificationResponse;
import com.example.shopapp.entity.User;
import com.example.shopapp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<NotificationResponse> notifications = notificationService.getNotifications(user.getEmail());
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        long unreadCount = notificationService.getUnreadCount(user.getEmail());
        return ResponseEntity.ok(unreadCount);
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        notificationService.markAllAsRead(user.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        boolean updated = notificationService.markAsRead(notificationId, user.getEmail());
        return updated ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
