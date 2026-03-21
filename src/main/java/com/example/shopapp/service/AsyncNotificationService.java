package com.example.shopapp.service;

import com.example.shopapp.entity.Meeting;
import com.example.shopapp.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service xử lý các notification tasks trong background thread.
 * Giúp API response ngay lập tức, không phải chờ gửi email/notification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNotificationService {

    private final NotificationService notificationService;
    private final MeetingRepository meetingRepository;

    /**
     * Xử lý mời người dùng vào cuộc họp trong background.
     * Bao gồm: lưu attendees + tạo notifications + gửi emails.
     */
    @Async
    public void processInvitationAsync(Long meetingId, List<String> attendeeEmails, String organizerEmail) {
        try {
            Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
            if (meeting == null) {
                log.warn("Meeting not found for async invitation processing: {}", meetingId);
                return;
            }

            notificationService.saveAttendees(meetingId, attendeeEmails);
            notificationService.createInvitationNotifications(meeting, attendeeEmails, organizerEmail);
            log.info("Async invitation processing completed for meeting: {}", meetingId);
        } catch (Exception e) {
            log.error("Error processing async invitation for meeting {}: {}", meetingId, e.getMessage(), e);
        }
    }

    /**
     * Xử lý thông báo cập nhật cuộc họp trong background.
     */
    @Async
    public void processMeetingUpdatedAsync(Long meetingId, String organizerEmail) {
        try {
            Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
            if (meeting == null) {
                log.warn("Meeting not found for async update notification: {}", meetingId);
                return;
            }

            notificationService.createMeetingUpdatedNotifications(meeting, organizerEmail);
            log.info("Async update notification completed for meeting: {}", meetingId);
        } catch (Exception e) {
            log.error("Error processing async update notification for meeting {}: {}", meetingId, e.getMessage(), e);
        }
    }

    /**
     * Xử lý thông báo huỷ cuộc họp trong background.
     */
    @Async
    public void processMeetingCancelledAsync(Long meetingId, String organizerEmail) {
        try {
            Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
            if (meeting == null) {
                log.warn("Meeting not found for async cancel notification: {}", meetingId);
                return;
            }

            notificationService.createMeetingCancelledNotifications(meeting, organizerEmail);
            log.info("Async cancel notification completed for meeting: {}", meetingId);
        } catch (Exception e) {
            log.error("Error processing async cancel notification for meeting {}: {}", meetingId, e.getMessage(), e);
        }
    }
}
