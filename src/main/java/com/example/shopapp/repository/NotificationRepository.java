package com.example.shopapp.repository;

import com.example.shopapp.entity.Notification;
import com.example.shopapp.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByRecipientEmailOrderByCreatedAtDesc(String recipientEmail);

    List<Notification> findAllByRecipientEmailAndIsReadFalse(String recipientEmail);

    Optional<Notification> findByIdAndRecipientEmail(Long id, String recipientEmail);

    long countByRecipientEmailAndIsReadFalse(String recipientEmail);

    boolean existsByMeetingIdAndTypeAndRecipientEmail(Long meetingId, NotificationType type, String recipientEmail);

    // Batch query để tìm tất cả recipients đã có notification trong 1 query
    List<Notification> findAllByMeetingIdAndTypeAndRecipientEmailIn(Long meetingId, NotificationType type, List<String> recipientEmails);
}
