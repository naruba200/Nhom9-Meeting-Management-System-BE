package com.example.shopapp.repository;

import com.example.shopapp.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByMeetingId(Long meetingId);
    
    List<Task> findByMeetingIdAndCreatedByEmail(Long meetingId, String createdByEmail);
    
    List<Task> findByAssigneeEmail(String assigneeEmail);

    List<Task> findByCreatedByEmail(String createdByEmail);
}
