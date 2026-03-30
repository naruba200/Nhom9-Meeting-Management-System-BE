package com.example.shopapp.controller;

import com.example.shopapp.dto.admin.AdminCreateUserRequest;
import com.example.shopapp.dto.admin.AdminDashboardStats;
import com.example.shopapp.dto.admin.AdminUpdateUserRequest;
import com.example.shopapp.dto.admin.AdminUserResponse;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.NotificationRepository;
import com.example.shopapp.repository.UserRepository;
import com.example.shopapp.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final NotificationRepository notificationRepository;
    private final AdminUserService adminUserService;

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

    // User Management APIs
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.getUserById(id));
    }

    @GetMapping("/users/email/{email}")
    public ResponseEntity<AdminUserResponse> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(adminUserService.getUserByEmail(email));
    }

    @PostMapping("/users")
    public ResponseEntity<AdminUserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.ok(adminUserService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> updateUser(@PathVariable Long id, 
                                                        @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(adminUserService.updateUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
