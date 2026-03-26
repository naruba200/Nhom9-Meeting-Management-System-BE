package com.example.shopapp.service;

import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final CloudinaryDatabaseBackupService cloudinaryDatabaseBackupService;

    /**
     * Chạy mỗi phút để tự động từ chối các lời mời quá hạn.
     * Tìm các invitation còn PENDING cho các cuộc họp đã bắt đầu (startTime < now).
     * Tự động chuyển sang DECLINED với lí do "Không phản hồi lời mời".
     */
    @Scheduled(cron = "0 * * * * *")  // Chạy mỗi phút
    @Transactional
    public void autoDeclineOverdueInvitations() {
        LocalDateTime now = LocalDateTime.now();

        log.info("[ScheduledTask] Running autoDeclineOverdueInvitations at {}", now);

        List<MeetingAttendee> pendingInvitations = meetingAttendeeRepository.findPendingInvitationsForPastMeetings(InvitationStatus.PENDING, now);

        log.info("[ScheduledTask] Found {} pending invitations for past meetings", pendingInvitations.size());

        if (pendingInvitations.isEmpty()) {
            return;
        }

        int declinedCount = 0;
        for (MeetingAttendee invitation : pendingInvitations) {
            invitation.setStatus(InvitationStatus.DECLINED);
            invitation.setResponseReason("Không phản hồi lời mời kịp thời");
            invitation.setRespondedAt(now);
            meetingAttendeeRepository.save(invitation);
            declinedCount++;

            log.info("[ScheduledTask] Auto-declined invitation for meeting {} attendee {}", 
                invitation.getMeetingId(), invitation.getEmail());
        }

        log.info("[ScheduledTask] Auto-declined {} invitations", declinedCount);
    }

    /**
     * Chạy tự động lúc 00:00 và 12:00 mỗi ngày để sao lưu CSDL lên Cloudinary.
     */
    @Scheduled(cron = "0 0 0,12 * * *", zone = "${backup.cloudinary.timezone:Asia/Ho_Chi_Minh}")
    public void autoBackupDatabaseToCloudinary() {
        try {
            log.info("[ScheduledTask] Running autoBackupDatabaseToCloudinary");
            cloudinaryDatabaseBackupService.createAndUploadBackup();
        } catch (Exception ex) {
            log.error("[ScheduledTask] Failed to upload scheduled DB backup to Cloudinary", ex);
        }
    }
}
