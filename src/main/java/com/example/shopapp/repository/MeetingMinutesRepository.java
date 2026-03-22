package com.example.shopapp.repository;

import com.example.shopapp.entity.MeetingMinutes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, Long> {
    Optional<MeetingMinutes> findByMeetingId(Long meetingId);
}
