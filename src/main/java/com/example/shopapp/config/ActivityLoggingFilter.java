package com.example.shopapp.config;

import com.example.shopapp.entity.ActivityActionType;
import com.example.shopapp.entity.ActivityEntityType;
import com.example.shopapp.entity.User;
import com.example.shopapp.service.ActivityLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ActivityLoggingFilter extends OncePerRequestFilter {

    private static final Pattern PATH_ID_PATTERN = Pattern.compile(".*/(\\d+)$");
    private static final Pattern ANY_ID_PATTERN = Pattern.compile("/(\\d+)(?:/|$)");
    private final ActivityLogService activityLogService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(request, response);

        String path = request.getServletPath();
        String method = request.getMethod();
        if (!shouldLogPath(path, method)) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            return;
        }

        try {
            ActivityActionType actionType = mapActionType(method, path);
            ActivityEntityType entityType = mapEntityType(path);
            Long entityId = extractEntityId(path);
            String description = buildDescription(method, path);

            activityLogService.logActivity(
                    user,
                    actionType,
                    entityType,
                    entityId,
                    description,
                    resolveClientIp(request),
                    request.getHeader("User-Agent"),
                    response.getStatus());
        } catch (Exception ex) {
            // Never break request flow due to activity logging failures.
            System.err.println("[ActivityLoggingFilter] Failed to log activity: " + ex.getMessage());
        }
    }

    private boolean shouldLogPath(String path, String method) {
        if (path == null || !path.startsWith("/api")) {
            return false;
        }

        // Keep auth noise low: only log meaningful authenticated auth actions.
        if (path.startsWith("/api/auth")) {
            boolean isProfileOrGoogleAction = path.startsWith("/api/auth/profile") || path.startsWith("/api/auth/google");
            boolean isPasswordReset = path.startsWith("/api/auth/reset-password");
            boolean isLogout = path.startsWith("/api/auth/logout");
            if (!isProfileOrGoogleAction && !isPasswordReset && !isLogout) {
                return false;
            }
        }

        // Skip reading logs endpoint to avoid self-generated noise loops.
        if (path.startsWith("/api/admin/activities")) {
            return false;
        }

        // Skip technical test endpoints.
        if (path.startsWith("/api/test")) {
            return false;
        }

        // Explicitly logged in AdminUserService to include deletion details.
        if ("DELETE".equalsIgnoreCase(method) && path.matches("^/api/admin/users/\\d+$")) {
            return false;
        }

        // User requested to avoid logging page-view/read operations.
        // Keep only action-oriented GET endpoints such as export/download.
        if ("GET".equalsIgnoreCase(method)
                && !path.contains("/download")
                && !path.contains("/export")) {
            return false;
        }

        return true;
    }

    private ActivityActionType mapActionType(String method, String path) {
        if (path != null) {
            if (path.contains("/invite") || path.contains("/accept") || path.contains("/decline") || path.contains("/cancel")) {
                return ActivityActionType.UPDATE;
            }
        }

        if ("GET".equalsIgnoreCase(method) && path != null) {
            if (path.contains("/download")) {
                return ActivityActionType.DOWNLOAD;
            }
            if (path.contains("/export")) {
                return ActivityActionType.EXPORT;
            }
        }

        return switch (method.toUpperCase()) {
            case "POST" -> ActivityActionType.CREATE;
            case "PUT", "PATCH" -> ActivityActionType.UPDATE;
            case "DELETE" -> ActivityActionType.DELETE;
            default -> ActivityActionType.VIEW;
        };
    }

    private ActivityEntityType mapEntityType(String path) {
        if (path.contains("/users")) {
            return ActivityEntityType.USER;
        }
        if (path.contains("/files")) {
            return ActivityEntityType.FILE;
        }
        if (path.contains("/meetings")) {
            return ActivityEntityType.MEETING;
        }
        if (path.contains("/notifications")) {
            return ActivityEntityType.NOTIFICATION;
        }
        if (path.contains("/tasks")) {
            return ActivityEntityType.TASK;
        }
        if (path.contains("/invitations")) {
            return ActivityEntityType.INVITATION;
        }
        if (path.contains("/minutes")) {
            return ActivityEntityType.MINUTES;
        }
        if (path.contains("/meeting-minutes")) {
            return ActivityEntityType.MINUTES;
        }
        return ActivityEntityType.SYSTEM;
    }

    private String buildDescription(String method, String path) {
        if (path == null) {
            return method + " /api";
        }

        // Admin User Management
        if ("POST".equalsIgnoreCase(method) && path.matches("^/api/admin/users$")) {
            return "Created new user";
        }
        if ("PUT".equalsIgnoreCase(method) && path.matches("^/api/admin/users/\\d+$")) {
            return "Updated user";
        }
        if ("DELETE".equalsIgnoreCase(method) && path.matches("^/api/admin/users/\\d+$")) {
            return "Deleted user";
        }

        // Authentication
        if (path.matches("^/api/auth/reset-password$")) {
            return "Reset password";
        }
        if (path.matches("^/api/auth/logout$")) {
            return "Logged out";
        }

        // Meeting Management
        if (path.matches("^/api/meetings/\\d+/invite$")) {
            return "Sent meeting invitation";
        }
        if (path.matches("^/api/meetings/\\d+/agenda$")) {
            return "Updated meeting agenda";
        }
        if (path.matches("^/api/meetings/\\d+/cancel$")) {
            return "Canceled meeting";
        }
        if (path.matches("^/api/meetings/\\d+/attachments/signature$")) {
            return "Requested attachment upload signature";
        }
        if (path.matches("^/api/meetings/\\d+/attachments/confirm$")) {
            return "Confirmed meeting attachment upload";
        }
        if (path.matches("^/api/meetings/\\d+/attachments/\\d+$")) {
            return "Deleted meeting attachment";
        }
        if (path.matches("^/api/meetings/\\d+/attendees$")) {
            return "Removed attendee from meeting";
        }
        if ("POST".equalsIgnoreCase(method) && path.matches("^/api/meetings$")) {
            return "Created meeting";
        }
        if ("PUT".equalsIgnoreCase(method) && path.matches("^/api/meetings/\\d+$")) {
            return "Updated meeting";
        }

        // Invitation Management
        if (path.matches("^/api/invitations/\\d+/accept$")) {
            return "Accepted invitation";
        }
        if (path.matches("^/api/invitations/\\d+/decline$")) {
            return "Declined invitation";
        }

        return method + " " + path;
    }

    private Long extractEntityId(String path) {
        Matcher matcher = PATH_ID_PATTERN.matcher(path);
        if (matcher.matches()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        Matcher anyIdMatcher = ANY_ID_PATTERN.matcher(path);
        if (anyIdMatcher.find()) {
            try {
                return Long.parseLong(anyIdMatcher.group(1));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}