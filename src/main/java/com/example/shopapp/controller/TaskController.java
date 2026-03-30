package com.example.shopapp.controller;

import com.example.shopapp.dto.task.CreateTaskRequest;
import com.example.shopapp.dto.task.TaskResponse;
import com.example.shopapp.dto.task.UpdateTaskRequest;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.TaskStatus;
import com.example.shopapp.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/my-tasks")
    public ResponseEntity<List<TaskResponse>> getMyTasks(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<TaskResponse> tasks = taskService.getTasksForUser(user.getEmail());
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/meeting/{meetingId}")
    public ResponseEntity<List<TaskResponse>> getTasksByMeeting(
            @PathVariable Long meetingId,
            Authentication authentication) {
        List<TaskResponse> tasks = taskService.getTasksByMeeting(meetingId);
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable Long taskId,
            Authentication authentication) {
        TaskResponse task = taskService.getTaskById(taskId);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/meeting/{meetingId}")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable Long meetingId,
            @Valid @RequestBody CreateTaskRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        TaskResponse response = taskService.createTask(meetingId, request, organizer);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        TaskResponse response = taskService.updateTask(taskId, request, organizer);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{taskId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateTaskStatus(
            @PathVariable Long taskId,
            @RequestParam TaskStatus status,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        taskService.updateTaskStatus(taskId, status, user);
    }

    @PutMapping("/subtask/{subtaskId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSubtaskStatus(
            @PathVariable Long subtaskId,
            @RequestParam TaskStatus status,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        taskService.updateSubtaskStatus(subtaskId, status, user);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(
            @PathVariable Long taskId,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        taskService.deleteTask(taskId, organizer);
    }
}
