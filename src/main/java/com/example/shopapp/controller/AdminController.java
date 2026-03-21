package com.example.shopapp.controller;

import com.example.shopapp.dto.admin.AdminDashboardStats;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.NotificationRepository;
import com.example.shopapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final NotificationRepository notificationRepository;

    @GetMapping("/dashboard/stats")
    public ResponseEntity<AdminDashboardStats> getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalMeetings = meetingRepository.count();
        long activeMeetings = meetingRepository.countByStatus(MeetingStatus.SCHEDULED);
        long totalNotifications = notificationRepository.count();

        AdminDashboardStats stats = AdminDashboardStats.builder()
                .totalUsers(totalUsers)
                .totalMeetings(totalMeetings)
                .activeMeetings(activeMeetings)
                .totalNotifications(totalNotifications)
                .build();

        return ResponseEntity.ok(stats);
    }
}
