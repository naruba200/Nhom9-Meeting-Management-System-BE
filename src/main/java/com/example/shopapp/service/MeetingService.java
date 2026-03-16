package com.example.shopapp.service;

import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.MeetingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, User organizer) {
        validateMeetingTime(request.getStartTime(), request.getEndTime());

        LocalDateTime now = LocalDateTime.now();
        boolean syncWithGoogleCalendar = Boolean.TRUE.equals(request.getSyncWithGoogleCalendar());

        String meetingLink = request.getExternalMeetingLink();
        String googleCalendarEventId = null;

        if (syncWithGoogleCalendar) {
            GoogleCalendarService.GoogleCalendarSyncResult syncResult = googleCalendarService
                .createEventWithMeetLink(request, organizer);
            googleCalendarEventId = syncResult.eventId();
            if (syncResult.meetLink() != null && !syncResult.meetLink().isBlank()) {
            meetingLink = syncResult.meetLink();
            }
        }

        Meeting meeting = Meeting.builder()
                .title(request.getTitle().trim())
                .agenda(request.getAgenda())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .organizerEmail(organizer.getEmail())
            .meetingLink(meetingLink)
            .googleCalendarEventId(googleCalendarEventId)
            .syncedWithGoogleCalendar(syncWithGoogleCalendar)
                .status(MeetingStatus.SCHEDULED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Meeting savedMeeting = meetingRepository.save(meeting);
        return toResponse(savedMeeting);
    }

    public List<MeetingResponse> getMeetingsByOrganizer(User organizer) {
        return meetingRepository.findAllByOrganizerEmailOrderByStartTimeDesc(organizer.getEmail())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateMeetingTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }

    private MeetingResponse toResponse(Meeting meeting) {
        return MeetingResponse.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .agenda(meeting.getAgenda())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .organizerEmail(meeting.getOrganizerEmail())
            .meetingLink(meeting.getMeetingLink())
            .googleCalendarEventId(meeting.getGoogleCalendarEventId())
            .syncedWithGoogleCalendar(meeting.isSyncedWithGoogleCalendar())
                .status(meeting.getStatus())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .build();
    }
}
