package com.example.shopapp.repository;

import com.example.shopapp.entity.Meeting;
import com.example.shopapp.enums.MeetingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
	List<Meeting> findAllByOrganizerEmailOrderByStartTimeDesc(String organizerEmail);

	List<Meeting> findAllByIdInOrderByStartTimeDesc(List<Long> ids);

	List<Meeting> findAllByOrganizerEmailAndStatusAndEndTimeBefore(String organizerEmail, MeetingStatus status, LocalDateTime endTime);

	List<Meeting> findAllByStatusAndStartTimeBetween(MeetingStatus status, LocalDateTime from, LocalDateTime to);

	long countByStatus(MeetingStatus status);
}
