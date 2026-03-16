package com.example.shopapp.repository;

import com.example.shopapp.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {
	List<Meeting> findAllByOrganizerEmailOrderByStartTimeDesc(String organizerEmail);
}
