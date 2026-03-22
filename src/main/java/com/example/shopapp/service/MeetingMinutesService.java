package com.example.shopapp.service;

import com.example.shopapp.dto.minutes.CreateMeetingMinutesRequest;
import com.example.shopapp.dto.minutes.MeetingMinutesResponse;
import com.example.shopapp.dto.minutes.MinutesSignatureResponse;
import com.example.shopapp.dto.minutes.MinutesTaskResponse;
import com.example.shopapp.dto.minutes.SignMeetingMinutesRequest;
import com.example.shopapp.dto.minutes.UpdateMeetingMinutesRequest;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingMinutes;
import com.example.shopapp.entity.MinutesSignature;
import com.example.shopapp.entity.MinutesTask;
import com.example.shopapp.entity.Task;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.MinutesStatus;
import com.example.shopapp.repository.MeetingMinutesRepository;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.MinutesSignatureRepository;
import com.example.shopapp.repository.MinutesTaskRepository;
import com.example.shopapp.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingMinutesService {

    private final MeetingMinutesRepository minutesRepository;
    private final MinutesSignatureRepository signatureRepository;
    private final MinutesTaskRepository minutesTaskRepository;
    private final MeetingRepository meetingRepository;
    private final TaskRepository taskRepository;

    public MeetingMinutesResponse getMinutesByMeeting(Long meetingId) {
        MeetingMinutes minutes = minutesRepository.findByMeetingId(meetingId)
                .orElse(null);
        if (minutes == null) {
            return null;
        }
        return toResponse(minutes);
    }

    public MeetingMinutesResponse getMinutesById(Long minutesId) {
        MeetingMinutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Minutes not found"));
        return toResponse(minutes);
    }

    @Transactional
    public MeetingMinutesResponse createMinutes(Long meetingId, CreateMeetingMinutesRequest request, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can create minutes");
        }

        // Check if minutes already exist for this meeting
        if (minutesRepository.findByMeetingId(meetingId).isPresent()) {
            throw new RuntimeException("Minutes already exist for this meeting");
        }

        MeetingMinutes minutes = MeetingMinutes.builder()
                .meetingId(meetingId)
                .title(request.getTitle().trim())
            .noteTakerEmail(organizer.getEmail())
                .location(request.getLocation() != null ? request.getLocation().trim() : null)
                .purpose(request.getPurpose())
                .attendees(request.getAttendees())
                .absentees(request.getAbsentees())
                .content(request.getContent())
                .decisions(request.getDecisions())
                .contributions(request.getContributions())
                .voting(request.getVoting())
                .conclusions(request.getConclusions())
                .minutesCreatedAt(request.getMinutesCreatedAt())
                .minutesClosedAt(request.getMinutesClosedAt())
                .status(MinutesStatus.DRAFT)
                .signatures(new ArrayList<>())
                .tasks(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        minutes = minutesRepository.save(minutes);

        // Add tasks if provided
        if (request.getTaskIds() != null && !request.getTaskIds().isEmpty()) {
            for (Long taskId : request.getTaskIds()) {
                Task task = taskRepository.findById(taskId)
                        .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

                MinutesTask minutesTask = MinutesTask.builder()
                        .minutes(minutes)
                        .taskId(task.getId())
                        .taskTitle(task.getTitle())
                        .assigneeEmail(task.getAssigneeEmail())
                        .createdAt(LocalDateTime.now())
                        .build();

                minutes.getTasks().add(minutesTaskRepository.save(minutesTask));
            }
            minutes = minutesRepository.save(minutes);
        }

        return toResponse(minutes);
    }

    @Transactional
    public MeetingMinutesResponse updateMinutes(Long minutesId, UpdateMeetingMinutesRequest request, User organizer) {
        MeetingMinutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Minutes not found"));

        Meeting meeting = meetingRepository.findById(minutes.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can update minutes");
        }

        if (minutes.getStatus() != MinutesStatus.DRAFT) {
            throw new RuntimeException("Cannot update minutes that have been finalized or signed");
        }

        minutes.setTitle(request.getTitle().trim());
        minutes.setLocation(request.getLocation() != null ? request.getLocation().trim() : null);
        minutes.setPurpose(request.getPurpose());
        minutes.setAttendees(request.getAttendees());
        minutes.setAbsentees(request.getAbsentees());
        minutes.setContent(request.getContent());
        minutes.setDecisions(request.getDecisions());
        minutes.setContributions(request.getContributions());
        minutes.setVoting(request.getVoting());
        minutes.setConclusions(request.getConclusions());

        if (request.getMinutesCreatedAt() != null) {
            minutes.setMinutesCreatedAt(request.getMinutesCreatedAt());
        }
        if (request.getMinutesClosedAt() != null) {
            minutes.setMinutesClosedAt(request.getMinutesClosedAt());
        }

        minutes.setUpdatedAt(LocalDateTime.now());

        // Update tasks
        minutesTaskRepository.deleteAll(minutes.getTasks());
        minutes.getTasks().clear();

        if (request.getTaskIds() != null && !request.getTaskIds().isEmpty()) {
            for (Long taskId : request.getTaskIds()) {
                Task task = taskRepository.findById(taskId)
                        .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

                MinutesTask minutesTask = MinutesTask.builder()
                        .minutes(minutes)
                        .taskId(task.getId())
                        .taskTitle(task.getTitle())
                        .assigneeEmail(task.getAssigneeEmail())
                        .createdAt(LocalDateTime.now())
                        .build();

                minutes.getTasks().add(minutesTaskRepository.save(minutesTask));
            }
        }

        minutes = minutesRepository.save(minutes);
        return toResponse(minutes);
    }

    @Transactional
    public MeetingMinutesResponse signMinutes(Long minutesId, SignMeetingMinutesRequest request, User signer) {
        MeetingMinutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Minutes not found"));
        final MeetingMinutes currentMinutes = minutes;

        // Check if signer is either organizer or attendee
        Meeting meeting = meetingRepository.findById(minutes.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        String signerEmail = normalizeEmail(request.getSignerEmail());

        MinutesSignature existingSignature = signatureRepository
            .findByMinutesIdAndSignerEmail(minutesId, signerEmail)
            .orElse(null);
        if (existingSignature != null && existingSignature.getSignedAt() != null) {
            throw new RuntimeException("Bạn đã ký biên bản này rồi");
        }

        // Update or create signature
        MinutesSignature signature = signatureRepository.findByMinutesIdAndSignerEmail(minutesId, signerEmail)
                .orElseGet(() -> {
                    MinutesSignature newSignature = MinutesSignature.builder()
                    .minutes(currentMinutes)
                            .signerEmail(signerEmail)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return newSignature;
                });

        signature.setSignerName(request.getSignerName());
        signature.setAgreed(request.isAgreed());
        signature.setNotes(request.getNotes());
        signature.setSignedAt(LocalDateTime.now());

        signatureRepository.save(signature);

        // Update minutes status if all required parties have signed
        updateMinutesStatusIfAllSigned(minutes);

        minutes = minutesRepository.save(minutes);
        MeetingMinutes refreshedMinutes = minutesRepository.findById(minutesId)
            .orElseThrow(() -> new RuntimeException("Minutes not found"));
        return toResponse(refreshedMinutes);
    }

    @Transactional
    public void finalizeMinutes(Long minutesId, User organizer) {
        MeetingMinutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Minutes not found"));

        Meeting meeting = meetingRepository.findById(minutes.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can finalize minutes");
        }

        minutes.setStatus(MinutesStatus.FINALIZED);
        minutes.setUpdatedAt(LocalDateTime.now());
        minutesRepository.save(minutes);
    }

    @Transactional
    public void deleteMinutes(Long minutesId, User organizer) {
        MeetingMinutes minutes = minutesRepository.findById(minutesId)
                .orElseThrow(() -> new RuntimeException("Minutes not found"));

        Meeting meeting = meetingRepository.findById(minutes.getMeetingId())
                .orElseThrow(() -> new RuntimeException("Meeting not found"));

        if (!meeting.getOrganizerEmail().equalsIgnoreCase(organizer.getEmail())) {
            throw new RuntimeException("Only meeting organizer can delete minutes");
        }

        minutesRepository.delete(minutes);
    }

    private void updateMinutesStatusIfAllSigned(MeetingMinutes minutes) {
        // Keep signature workflow without forcing status transition that may not be supported
        // by existing DB enum values in deployed environments.
        List<MinutesSignature> signatures = signatureRepository.findByMinutesId(minutes.getId());
        boolean organizerHasSigned = signatures.stream()
                .anyMatch(sig -> sig.isAgreed());

        if (organizerHasSigned && minutes.getStatus() == MinutesStatus.FINALIZED) {
            minutes.setUpdatedAt(LocalDateTime.now());
        }
    }

    private MeetingMinutesResponse toResponse(MeetingMinutes minutes) {
        List<MinutesSignatureResponse> signatureResponses = minutes.getSignatures()
                .stream()
                .map(this::toSignatureResponse)
                .collect(Collectors.toList());

        List<MinutesTaskResponse> taskResponses = minutes.getTasks()
                .stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());

        return MeetingMinutesResponse.builder()
                .id(minutes.getId())
                .meetingId(minutes.getMeetingId())
                .title(minutes.getTitle())
                .location(minutes.getLocation())
                .purpose(minutes.getPurpose())
                .attendees(minutes.getAttendees())
                .absentees(minutes.getAbsentees())
                .content(minutes.getContent())
                .decisions(minutes.getDecisions())
                .contributions(minutes.getContributions())
                .voting(minutes.getVoting())
                .conclusions(minutes.getConclusions())
                .minutesCreatedAt(minutes.getMinutesCreatedAt())
                .minutesClosedAt(minutes.getMinutesClosedAt())
                .status(minutes.getStatus())
                .signatures(signatureResponses)
                .tasks(taskResponses)
                .createdAt(minutes.getCreatedAt())
                .updatedAt(minutes.getUpdatedAt())
                .pdfUrl(minutes.getPdfUrl())
                .build();
    }

    private MinutesSignatureResponse toSignatureResponse(MinutesSignature signature) {
        return MinutesSignatureResponse.builder()
                .id(signature.getId())
                .signerEmail(signature.getSignerEmail())
                .signerName(signature.getSignerName())
                .signedAt(signature.getSignedAt())
                .agreed(signature.isAgreed())
                .notes(signature.getNotes())
                .createdAt(signature.getCreatedAt())
                .build();
    }

    private MinutesTaskResponse toTaskResponse(MinutesTask task) {
        return MinutesTaskResponse.builder()
                .id(task.getId())
                .taskId(task.getTaskId())
                .taskTitle(task.getTaskTitle())
                .assigneeEmail(task.getAssigneeEmail())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase() : "";
    }
}
