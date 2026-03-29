package com.example.shopapp.service;

import com.example.shopapp.dto.task.CreateTaskRequest;
import com.example.shopapp.dto.task.SubtaskResponse;
import com.example.shopapp.dto.task.TaskResponse;
import com.example.shopapp.dto.task.UpdateTaskRequest;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.Subtask;
import com.example.shopapp.entity.Task;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.enums.NotificationType;
import com.example.shopapp.enums.TaskStatus;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.SubtaskRepository;
import com.example.shopapp.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final NotificationService notificationService;

    public List<TaskResponse> getTasksByMeeting(Long meetingId) {
        return taskRepository.findByMeetingId(meetingId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getTasksForUser(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);

        // Get tasks assigned to user OR created by user
        List<Task> assignedTasks = taskRepository.findByAssigneeEmail(normalizedEmail);
        List<Task> createdTasks = taskRepository.findByCreatedByEmail(normalizedEmail);

        // Merge and deduplicate
        Set<Long> seenIds = new HashSet<>();
        List<Task> allTasks = new ArrayList<>();

        for (Task task : assignedTasks) {
            if (seenIds.add(task.getId())) {
                allTasks.add(task);
            }
        }
        for (Task task : createdTasks) {
            if (seenIds.add(task.getId())) {
                allTasks.add(task);
            }
        }

        return allTasks.stream()
                .map(this::toResponseWithMeetingTitle)
                .collect(Collectors.toList());
    }

    public TaskResponse getTaskById(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return toResponse(task);
    }

    @Transactional
    public TaskResponse createTask(Long meetingId, CreateTaskRequest request, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can create tasks");
        }

        // Check if assignee has accepted the meeting invitation
        String assigneeEmail = normalizeEmail(request.getAssigneeEmail());
        MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(meetingId, assigneeEmail)
                .orElseThrow(() -> new BadRequestException("Assignee is not invited to this meeting."));

        if (attendee.getStatus() != InvitationStatus.ACCEPTED) {
            throw new BadRequestException("Task assignee has not accepted the meeting invitation.");
        }

        Task task = Task.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .status(TaskStatus.TODO)
                .assigneeEmail(assigneeEmail)
                .createdByEmail(organizer.getEmail())
                .meetingId(meetingId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .dueDate(request.getDueDate())
                .subtasks(new ArrayList<>())
                .build();

        task = taskRepository.save(task);

        // Add subtasks if provided
        if (request.getSubtaskTitles() != null && !request.getSubtaskTitles().isEmpty()) {
            for (String subtaskTitle : request.getSubtaskTitles()) {
                if (subtaskTitle != null && !subtaskTitle.trim().isEmpty()) {
                    Subtask subtask = Subtask.builder()
                            .title(subtaskTitle.trim())
                            .status(TaskStatus.TODO)
                            .task(task)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    task.getSubtasks().add(subtaskRepository.save(subtask));
                }
            }
            task = taskRepository.save(task);
        }

        // Send notification to assignee
        String meetingTitle = meeting.getTitle();
        String title = "Bạn được giao công việc mới";
        String message = "Bạn được giao công việc \"" + request.getTitle() + "\" trong cuộc họp \"" + meetingTitle + "\"";
        notificationService.createNotification(
                request.getAssigneeEmail(),
                meetingId,
                NotificationType.TASK_ASSIGNED,
                title,
                message
        );

        return toResponse(task);
    }

    @Transactional
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request, User organizer) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Meeting meeting = meetingRepository.findById(task.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can update tasks");
        }

        // Check if the new assignee has accepted the meeting invitation
        String newAssigneeEmail = normalizeEmail(request.getAssigneeEmail());
        if (!newAssigneeEmail.equalsIgnoreCase(task.getAssigneeEmail())) {
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(task.getMeetingId(), newAssigneeEmail)
                    .orElseThrow(() -> new BadRequestException("New assignee is not invited to this meeting."));

            if (attendee.getStatus() != InvitationStatus.ACCEPTED) {
                throw new BadRequestException("New task assignee has not accepted the meeting invitation.");
            }
        }


        task.setTitle(request.getTitle().trim());
        task.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        task.setAssigneeEmail(newAssigneeEmail);
        task.setDueDate(request.getDueDate());
        task.setUpdatedAt(LocalDateTime.now());

        // Clear existing subtasks
        subtaskRepository.deleteAll(task.getSubtasks());
        task.getSubtasks().clear();

        // Add new subtasks if provided
        if (request.getSubtaskTitles() != null && !request.getSubtaskTitles().isEmpty()) {
            for (String subtaskTitle : request.getSubtaskTitles()) {
                if (subtaskTitle != null && !subtaskTitle.trim().isEmpty()) {
                    Subtask subtask = Subtask.builder()
                            .title(subtaskTitle.trim())
                            .status(TaskStatus.TODO)
                            .task(task)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    task.getSubtasks().add(subtaskRepository.save(subtask));
                }
            }
        }

        task = taskRepository.save(task);

        // Send update notification
        String meetingTitle = meeting.getTitle();
        String notifTitle = "Công việc được cập nhật";
        String message = "Công việc \"" + request.getTitle() + "\" trong cuộc họp \"" + meetingTitle + "\" đã được cập nhật";
        notificationService.createNotification(
                request.getAssigneeEmail(),
                meeting.getId(),
                NotificationType.TASK_UPDATED,
                notifTitle,
                message
        );

        return toResponse(task);
    }

    @Transactional
    public void updateTaskStatus(Long taskId, TaskStatus status, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Check if user is either the organizer or the assignee
        Meeting meeting = meetingRepository.findById(task.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        String userEmail = normalizeEmail(user.getEmail());
        if (!meeting.getOrganizerEmail().equalsIgnoreCase(userEmail) &&
                !task.getAssigneeEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("You don't have permission to update this task status");
        }

        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());

        if (status == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }

        taskRepository.save(task);
    }

    @Transactional
    public void updateSubtaskStatus(Long subtaskId, TaskStatus status, User user) {
        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new RuntimeException("Subtask not found"));

        Task task = subtask.getTask();
        Meeting meeting = meetingRepository.findById(task.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        String userEmail = normalizeEmail(user.getEmail());
        if (!meeting.getOrganizerEmail().equalsIgnoreCase(userEmail) &&
                !task.getAssigneeEmail().equalsIgnoreCase(userEmail)) {
            throw new RuntimeException("You don't have permission to update this subtask");
        }

        subtask.setStatus(status);
        subtask.setUpdatedAt(LocalDateTime.now());

        if (status == TaskStatus.COMPLETED) {
            subtask.setCompletedAt(LocalDateTime.now());
        } else {
            subtask.setCompletedAt(null);
        }

        subtaskRepository.save(subtask);
    }

    @Transactional
    public void deleteTask(Long taskId, User organizer) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Meeting meeting = meetingRepository.findById(task.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can delete tasks");
        }

        taskRepository.delete(task);
    }

    private TaskResponse toResponse(Task task) {
        List<SubtaskResponse> subtaskResponses = task.getSubtasks()
                .stream()
                .map(this::toSubtaskResponse)
                .collect(Collectors.toList());

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .assigneeEmail(task.getAssigneeEmail())
                .createdByEmail(task.getCreatedByEmail())
                .meetingId(task.getMeetingId())
                .subtasks(subtaskResponses)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt(task.getCompletedAt())
                .dueDate(task.getDueDate())
                .build();
    }

    private TaskResponse toResponseWithMeetingTitle(Task task) {
        List<SubtaskResponse> subtaskResponses = task.getSubtasks()
                .stream()
                .map(this::toSubtaskResponse)
                .collect(Collectors.toList());

        String meetingTitle = meetingRepository.findById(task.getMeetingId())
                .map(Meeting::getTitle)
                .orElse("Unknown Meeting");

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .assigneeEmail(task.getAssigneeEmail())
                .createdByEmail(task.getCreatedByEmail())
                .meetingId(task.getMeetingId())
                .meetingTitle(meetingTitle)
                .subtasks(subtaskResponses)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completedAt(task.getCompletedAt())
                .dueDate(task.getDueDate())
                .build();
    }

    private SubtaskResponse toSubtaskResponse(Subtask subtask) {
        return SubtaskResponse.builder()
                .id(subtask.getId())
                .title(subtask.getTitle())
                .status(subtask.getStatus())
                .createdAt(subtask.getCreatedAt())
                .updatedAt(subtask.getUpdatedAt())
                .completedAt(subtask.getCompletedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : "";
    }
}
