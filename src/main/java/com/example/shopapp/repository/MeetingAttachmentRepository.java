package com.example.shopapp.repository;

import com.example.shopapp.entity.MeetingAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingAttachmentRepository extends JpaRepository<MeetingAttachment, Long> {
    List<MeetingAttachment> findAllByMeetingIdOrderByIdAsc(Long meetingId);
    Optional<MeetingAttachment> findByIdAndMeetingId(Long id, Long meetingId);
    Optional<MeetingAttachment> findByMeetingIdAndCloudPublicId(Long meetingId, String cloudPublicId);
    void deleteAllByMeetingId(Long meetingId);
}
