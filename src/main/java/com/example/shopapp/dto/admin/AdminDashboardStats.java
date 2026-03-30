package com.example.shopapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class AdminDashboardStats {
    private long totalUsers;
    private long totalMeetings;
    private long activeMeetings;
    private long totalNotifications;
}
