package com.example.shopapp.service;

import com.example.shopapp.dto.notification.NotificationResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.Notification;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.enums.NotificationType;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificationRepository notificationRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final MeetingRepository meetingRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendBaseUrl;

    public List<NotificationResponse> getNotifications(String recipientEmail) {
        return notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(recipientEmail)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public long getUnreadCount(String recipientEmail) {
        return notificationRepository.countByRecipientEmailAndIsReadFalse(normalizeEmail(recipientEmail));
    }

    @Transactional
    public void markAllAsRead(String recipientEmail) {
        List<Notification> unreadNotifications = notificationRepository
                .findAllByRecipientEmailAndIsReadFalse(normalizeEmail(recipientEmail));

        if (unreadNotifications.isEmpty()) {
            return;
        }

        unreadNotifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(unreadNotifications);
    }

    @Transactional
    public boolean markAsRead(Long notificationId, String recipientEmail) {
        Notification notification = notificationRepository
                .findByIdAndRecipientEmail(notificationId, normalizeEmail(recipientEmail))
                .orElse(null);

        if (notification == null) {
            return false;
        }

        if (notification.isRead()) {
            return true;
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        return true;
    }

    @Transactional
    public void createInvitationNotifications(Meeting meeting, List<String> attendeeEmails, String organizerEmail) {
        Set<String> recipients = normalizeRecipients(attendeeEmails);
        recipients.removeIf(email -> email.equalsIgnoreCase(organizerEmail));

        String title = "Lời mời cuộc họp mới";
        String meetingDetailUrl = buildMeetingDetailUrl(meeting.getId());
        String meetingJoinUrl = buildMeetingJoinUrl(meeting);
        String message = "Bạn được mời tham gia cuộc họp \"" + meeting.getTitle() + "\" lúc "
            + meeting.getStartTime().format(DATE_TIME_FORMATTER)
            + ". Xem chi tiết tại: " + meetingDetailUrl;

        createNotifications(recipients, meeting.getId(), NotificationType.MEETING_INVITATION, title, message);
        sendInvitationEmails(recipients, title, buildInvitationEmailBody(meeting, organizerEmail, meetingDetailUrl, meetingJoinUrl));
    }

    @Transactional
    public void createMeetingUpdatedNotifications(Meeting meeting, String organizerEmail) {
        Set<String> recipients = getRecipientsForMeeting(meeting.getId(), organizerEmail);

        String title = "Cuộc họp đã được chỉnh sửa";
        String message = "Cuộc họp \"" + meeting.getTitle() + "\" đã được cập nhật. Thời gian mới: "
                + meeting.getStartTime().format(DATE_TIME_FORMATTER) + ".";

        createNotifications(recipients, meeting.getId(), NotificationType.MEETING_UPDATED, title, message);
        sendMeetingEventEmails(recipients, title, message);
    }

    @Transactional
    public void createMeetingCancelledNotifications(Meeting meeting, String organizerEmail) {
        Set<String> recipients = getRecipientsForMeeting(meeting.getId(), organizerEmail);

        String title = "Cuộc họp đã bị hủy";
        String message = "Cuộc họp \"" + meeting.getTitle() + "\" đã bị hủy.";

        createNotifications(recipients, meeting.getId(), NotificationType.MEETING_CANCELLED, title, message);
        sendMeetingEventEmails(recipients, title, message);
    }

    @Transactional
    public void createInvitationDeclinedNotification(Meeting meeting, String attendeeEmail, String reason) {
        Set<String> recipients = Set.of(normalizeEmail(meeting.getOrganizerEmail()));
        String title = "Người tham gia đã từ chối lời mời";
        String message = "Người tham gia " + normalizeEmail(attendeeEmail) + " đã từ chối cuộc họp \""
                + meeting.getTitle() + "\". Lý do: " + reason;

        createNotifications(recipients, meeting.getId(), NotificationType.MEETING_INVITATION_DECLINED, title, message);
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void createStartingSoonNotifications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.plusMinutes(14);
        LocalDateTime to = now.plusMinutes(15);

        List<Meeting> meetings = meetingRepository.findAllByStatusAndStartTimeBetween(MeetingStatus.SCHEDULED, from, to);
        List<Notification> allNotificationsToSave = new ArrayList<>();

        for (Meeting meeting : meetings) {
            Set<String> recipients = getRecipientsForMeeting(meeting.getId(), meeting.getOrganizerEmail());

            String title = "Cuộc họp sắp bắt đầu";
            String message = "Cuộc họp \"" + meeting.getTitle() + "\" sẽ bắt đầu sau 15 phút.";

            // Batch query: tìm tất cả recipients đã có notification trong 1 query thay vì N queries
            List<Notification> existingNotifications = notificationRepository.findAllByMeetingIdAndTypeAndRecipientEmailIn(
                    meeting.getId(),
                    NotificationType.MEETING_STARTING_SOON,
                    new ArrayList<>(recipients)
            );
            Set<String> alreadyNotified = existingNotifications.stream()
                    .map(Notification::getRecipientEmail)
                    .collect(java.util.stream.Collectors.toSet());

            for (String recipient : recipients) {
                if (!alreadyNotified.contains(recipient)) {
                    allNotificationsToSave.add(Notification.builder()
                            .recipientEmail(recipient)
                            .title(title)
                            .message(message)
                            .type(NotificationType.MEETING_STARTING_SOON)
                            .isRead(false)
                            .meetingId(meeting.getId())
                            .createdAt(now)
                            .build());
                }
            }
        }

        // Batch save tất cả notifications trong 1 lần
        if (!allNotificationsToSave.isEmpty()) {
            notificationRepository.saveAll(allNotificationsToSave);
        }
    }

    @Transactional
    public void saveAttendees(Long meetingId, List<String> attendeeEmails) {
        List<String> normalized = new ArrayList<>(normalizeRecipients(attendeeEmails));
        if (normalized.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Batch query: fetch tất cả existing attendees trong 1 query thay vì N queries
        List<MeetingAttendee> existingAttendees = meetingAttendeeRepository.findAllByMeetingIdAndEmailIn(meetingId, normalized);
        Map<String, MeetingAttendee> existingByEmail = existingAttendees.stream()
                .collect(java.util.stream.Collectors.toMap(MeetingAttendee::getEmail, a -> a));

        List<MeetingAttendee> attendees = new ArrayList<>();

        for (String email : normalized) {
            MeetingAttendee attendee = existingByEmail.getOrDefault(email,
                    MeetingAttendee.builder()
                            .meetingId(meetingId)
                            .email(email)
                            .build());

            attendee.setStatus(InvitationStatus.PENDING);
            attendee.setResponseReason(null);
            attendee.setRespondedAt(null);
            attendee.setInvitedAt(now);
            attendees.add(attendee);
        }

        meetingAttendeeRepository.saveAll(attendees);
    }

    private Set<String> getRecipientsForMeeting(Long meetingId, String organizerEmail) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(normalizeEmail(organizerEmail));

        List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingId);
        for (MeetingAttendee attendee : attendees) {
            recipients.add(normalizeEmail(attendee.getEmail()));
        }

        recipients.removeIf(String::isBlank);
        return recipients;
    }

    private void createNotifications(Set<String> recipients,
                                     Long meetingId,
                                     NotificationType type,
                                     String title,
                                     String message) {
        if (recipients.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = recipients.stream()
                .map(recipient -> Notification.builder()
                        .recipientEmail(recipient)
                        .title(title)
                        .message(message)
                        .type(type)
                        .isRead(false)
                        .meetingId(meetingId)
                        .createdAt(now)
                        .build())
                .toList();

        notificationRepository.saveAll(notifications);
    }

    private Set<String> normalizeRecipients(List<String> emails) {
        Set<String> recipients = new LinkedHashSet<>();
        if (emails == null) {
            return recipients;
        }

        for (String email : emails) {
            String normalized = normalizeEmail(email);
            if (!normalized.isBlank()) {
                recipients.add(normalized);
            }
        }
        return recipients;
    }

    private void sendMeetingEventEmails(Set<String> recipients, String title, String message) {
        for (String recipient : recipients) {
            if (recipient.isBlank()) {
                continue;
            }
            emailService.sendEmailAsync(recipient, title, message + "\n\n(Vui lòng kiểm tra mục Thông báo trong hệ thống để biết chi tiết.)");
        }
    }

    private void sendInvitationEmails(Set<String> recipients, String title, String emailBody) {
        for (String recipient : recipients) {
            if (recipient.isBlank()) {
                continue;
            }
            emailService.sendEmailAsync(recipient, title, emailBody);
        }
    }

    private String buildInvitationEmailBody(Meeting meeting,
                                            String organizerEmail,
                                            String meetingDetailUrl,
                                            String meetingJoinUrl) {
        StringBuilder body = new StringBuilder();
        body.append("Xin chào,\n\n");
        body.append("Bạn vừa nhận được lời mời tham gia cuộc họp.\n\n");
        body.append("Thong tin cuộc họp:\n");
        body.append("- Tiêu đề: ").append(meeting.getTitle()).append("\n");
        body.append("- Người tổ chức: ").append(normalizeEmail(organizerEmail)).append("\n");
        body.append("- Bắt đầu: ").append(meeting.getStartTime().format(DATE_TIME_FORMATTER)).append("\n");
        body.append("- Kết thúc: ").append(meeting.getEndTime().format(DATE_TIME_FORMATTER)).append("\n");
        body.append("- Nội dung: ").append(meeting.getAgenda() == null || meeting.getAgenda().isBlank() ? "(Không có)" : meeting.getAgenda()).append("\n");
        body.append("- Mã cuộc họp: ").append(meeting.getId()).append("\n\n");

        body.append("Xem chi tiết cuộc họp tại:\n");
        body.append(meetingDetailUrl).append("\n\n");

        body.append("Đường link tham gia cuộc họp:\n");
        body.append(meetingJoinUrl).append("\n\n");

        body.append("Trân trọng,\n");
        body.append("Hệ thống Quản lý Cuộc họp");
        return body.toString();
    }

    private String buildMeetingDetailUrl(Long meetingId) {
        return frontendBaseUrl + "/meeting/history?keyword=" + meetingId;
    }

    private String buildMeetingJoinUrl(Meeting meeting) {
        if (meeting.getMeetingLink() != null && !meeting.getMeetingLink().isBlank()) {
            return meeting.getMeetingLink();
        }
        return frontendBaseUrl + "/meeting";
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .read(notification.isRead())
                .meetingId(notification.getMeetingId())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
