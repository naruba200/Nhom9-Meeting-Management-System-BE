package com.example.shopapp.service;

import com.example.shopapp.dto.ActivityLogResponse;
import com.example.shopapp.dto.PaginatedActivityLogResponse;
import com.example.shopapp.entity.ActivityLog;
import com.example.shopapp.entity.ActivityActionType;
import com.example.shopapp.entity.ActivityEntityType;
import com.example.shopapp.entity.User;
import com.example.shopapp.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Transactional
    public void logActivity(User user, ActivityActionType actionType, ActivityEntityType entityType,
                           Long entityId, String description, String ipAddress, String userAgent,
                           Integer statusCode) {
        logActivity(user, actionType, entityType, entityId, description, ipAddress, userAgent, statusCode, null);
    }

    @Transactional
    public void logActivity(User user, ActivityActionType actionType, ActivityEntityType entityType,
                           Long entityId, String description, String ipAddress, String userAgent,
                           Integer statusCode, String details) {
        System.out.println("[ActivityLogService] Logging activity: " + actionType + " on " + entityType);

        ActivityLog log = ActivityLog.builder()
                .user(user)
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .statusCode(statusCode)
                .details(details)
                .build();

        activityLogRepository.save(log);
    }

    public PaginatedActivityLogResponse getActivityLogs(
            int page, int size,
            String actionType, String entityType, String userEmail,
            String startDate, String endDate,
            String sortBy, String direction) {

        System.out.println("[ActivityLogService] Fetching activity logs with filters");

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.fromString(direction), "timestamp");

        ActivityActionType action = null;
        ActivityEntityType entity = null;
        LocalDateTime start = null;
        LocalDateTime end = null;

        try {
            if (actionType != null && !actionType.isEmpty()) {
                action = ActivityActionType.valueOf(actionType.toUpperCase());
            }
            if (entityType != null && !entityType.isEmpty()) {
                entity = ActivityEntityType.valueOf(entityType.toUpperCase());
            }
            if (startDate != null && !startDate.isEmpty()) {
                start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[ActivityLogService] Invalid filter parameters: " + e.getMessage());
        }

        Page<ActivityLog> logs = activityLogRepository.searchActivityLogs(action, entity, userEmail, start, end, pageable);

        return mapToResponse(logs);
    }

    public PaginatedActivityLogResponse getActivityLogsByUser(Long userId, int page, int size) {
        System.out.println("[ActivityLogService] Fetching activity logs for user: " + userId);

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "timestamp");
        Page<ActivityLog> logs = activityLogRepository.findByUserId(userId, pageable);

        return mapToResponse(logs);
    }

    public List<ActivityLogResponse> getActivityLogsByDateRange(String startDate, String endDate) {
        System.out.println("[ActivityLogService] Fetching activity logs for date range");

        LocalDateTime start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTime end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        List<ActivityLog> logs = activityLogRepository.findByDateRange(start, end);
        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ActivityLogResponse> getUserLoginLogoutHistory(Long userId) {
        System.out.println("[ActivityLogService] Fetching login/logout history for user: " + userId);

        List<ActivityLog> logs = activityLogRepository.findLoginLogoutHistory(userId);
        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PaginatedActivityLogResponse mapToResponse(Page<ActivityLog> logs) {
        List<ActivityLogResponse> content = logs.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PaginatedActivityLogResponse.builder()
                .content(content)
                .page(logs.getNumber())
                .size(logs.getSize())
                .totalElements(logs.getTotalElements())
                .totalPages(logs.getTotalPages())
                .first(logs.isFirst())
                .last(logs.isLast())
                .numberOfElements(logs.getNumberOfElements())
                .build();
    }

    private ActivityLogResponse mapToResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .userId(log.getUser().getId())
                .userEmail(log.getUser().getEmail())
                .userFullName(log.getUser().getFullName())
                .action(log.getActionType().toString())
                .actionType(log.getActionType().toString())
                .entityType(log.getEntityType().toString())
                .entityId(log.getEntityId())
                .description(log.getDescription())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .statusCode(log.getStatusCode())
                .timestamp(log.getTimestamp().format(DATE_FORMATTER))
                .details(log.getDetails())
                .build();
    }

    public byte[] exportActivityLogsToCSV(
            String actionType, String entityType, String userEmail,
            String startDate, String endDate) {
        System.out.println("[ActivityLogService] Exporting activity logs to CSV");

        ActivityActionType action = null;
        ActivityEntityType entity = null;
        LocalDateTime start = null;
        LocalDateTime end = null;

        try {
            if (actionType != null && !actionType.isEmpty()) {
                action = ActivityActionType.valueOf(actionType.toUpperCase());
            }
            if (entityType != null && !entityType.isEmpty()) {
                entity = ActivityEntityType.valueOf(entityType.toUpperCase());
            }
            if (startDate != null && !startDate.isEmpty()) {
                start = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[ActivityLogService] Invalid filter parameters: " + e.getMessage());
        }

        List<ActivityLog> logs = activityLogRepository.searchActivityLogsForExport(action, entity, userEmail, start, end);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,User Email,Full Name,Action,Entity Type,Entity ID,Description,Status Code,Timestamp,IP Address,User Agent\n");

        for (ActivityLog log : logs) {
            csv.append(escapeCsvValue(log.getId().toString())).append(",");
            csv.append(escapeCsvValue(log.getUser().getEmail())).append(",");
            csv.append(escapeCsvValue(log.getUser().getFullName())).append(",");
            csv.append(escapeCsvValue(log.getActionType().toString())).append(",");
            csv.append(escapeCsvValue(log.getEntityType().toString())).append(",");
            csv.append(escapeCsvValue(log.getEntityId() != null ? log.getEntityId().toString() : "")).append(",");
            csv.append(escapeCsvValue(log.getDescription())).append(",");
            csv.append(escapeCsvValue(log.getStatusCode() != null ? log.getStatusCode().toString() : "")).append(",");
            csv.append(escapeCsvValue(log.getTimestamp().format(DATE_FORMATTER))).append(",");
            csv.append(escapeCsvValue(log.getIpAddress())).append(",");
            csv.append(escapeCsvValue(log.getUserAgent())).append("\n");
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
