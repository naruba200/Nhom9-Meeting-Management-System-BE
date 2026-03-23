package com.example.shopapp.repository;

import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

public interface MeetingAttendeeRepository extends JpaRepository<MeetingAttendee, Long> {
    List<MeetingAttendee> findAllByMeetingId(Long meetingId);

    List<MeetingAttendee> findAllByEmailOrderByInvitedAtDesc(String email);

    List<MeetingAttendee> findAllByEmailAndStatus(String email, InvitationStatus status);

    Optional<MeetingAttendee> findByIdAndEmail(Long id, String email);

    Optional<MeetingAttendee> findByMeetingIdAndEmail(Long meetingId, String email);

    List<MeetingAttendee> findAllByMeetingIdAndStatus(Long meetingId, InvitationStatus status);

    // Batch query để tìm tất cả attendees theo meetingId và list emails trong 1 query
    List<MeetingAttendee> findAllByMeetingIdAndEmailIn(Long meetingId, List<String> emails);

    @Query("SELECT ma FROM MeetingAttendee ma JOIN Meeting m ON ma.meetingId = m.id WHERE ma.status = :status AND m.startTime < :time")
    List<MeetingAttendee> findPendingInvitationsForPastMeetings(@Param("status") InvitationStatus status, @Param("time") LocalDateTime time);
}
