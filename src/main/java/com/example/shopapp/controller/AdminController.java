package com.example.shopapp.controller;

import com.example.shopapp.dto.admin.AdminCreateUserRequest;
import com.example.shopapp.dto.admin.AdminDashboardStats;
import com.example.shopapp.dto.admin.AdminUpdateUserRequest;
import com.example.shopapp.dto.admin.AdminUserResponse;
import com.example.shopapp.dto.PaginatedActivityLogResponse;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.NotificationRepository;
import com.example.shopapp.repository.UserRepository;
import com.example.shopapp.service.AdminUserService;
import com.example.shopapp.service.ActivityLogService;
import com.example.shopapp.service.DatabaseBackupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final NotificationRepository notificationRepository;
    private final AdminUserService adminUserService;
    private final ActivityLogService activityLogService;
    private final DatabaseBackupService databaseBackupService;

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

    // Activity Log APIs
    @GetMapping("/activities")
    public ResponseEntity<PaginatedActivityLogResponse> getActivityLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        System.out.println("[AdminController] Fetching activity logs");
        PaginatedActivityLogResponse response = activityLogService.getActivityLogs(
                page, size, actionType, entityType, userEmail, startDate, endDate, sortBy, direction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activities/user/{userId}")
    public ResponseEntity<PaginatedActivityLogResponse> getActivityLogsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        System.out.println("[AdminController] Fetching activity logs for user: " + userId);
        PaginatedActivityLogResponse response = activityLogService.getActivityLogsByUser(userId, page, size);
        return ResponseEntity.ok(response);
    }

    // Database Backup APIs
    @PostMapping("/database/backup")
    public ResponseEntity<?> createDatabaseBackup() {
        try {
            System.out.println("[AdminController] Creating database backup...");
            Map<String, Object> backupResult = databaseBackupService.createDatabaseBackup();
            byte[] backupData = (byte[]) backupResult.get("data");
            String fileName = (String) backupResult.get("fileName");

            Resource resource = databaseBackupService.createDownloadResource(backupData, fileName);

            System.out.println("[AdminController] Backup created successfully. Size: " + backupData.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            ContentDisposition.attachment()
                                    .filename(fileName)
                                    .build()
                                    .toString())
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .header("Content-Length", String.valueOf(backupData.length))
                    .body(resource);
        } catch (Exception e) {
            System.err.println("[AdminController] Error creating database backup: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "success", false,
                            "message", "Không thể tạo backup cơ sở dữ liệu: " + e.getMessage()
                    ));
        }
    }

    @PostMapping("/database/backup/cloudinary")
    public ResponseEntity<Map<String, Object>> createCloudinaryBackup() {
        try {
            Map<String, Object> result = databaseBackupService.createCloudinaryBackup();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "success", false,
                            "message", "Không thể tạo backup lên Cloudinary: " + e.getMessage()
                    ));
        }
    }

    @GetMapping("/database/backup/last-time")
    public ResponseEntity<Map<String, String>> getLastBackupTime() {
        String lastBackupTime = databaseBackupService.getLastBackupTimeFormatted();
        return ResponseEntity.ok(Map.of(
                "lastBackupTime", lastBackupTime
        ));
    }
}
