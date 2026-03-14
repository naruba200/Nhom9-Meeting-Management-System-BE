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

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;

    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, User organizer) {
        validateMeetingTime(request.getStartTime(), request.getEndTime());

        LocalDateTime now = LocalDateTime.now();

        Meeting meeting = Meeting.builder()
                .title(request.getTitle().trim())
                .agenda(request.getAgenda())
                .room(request.getRoom().trim())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .organizerEmail(organizer.getEmail())
                .status(MeetingStatus.SCHEDULED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Meeting savedMeeting = meetingRepository.save(meeting);
        return toResponse(savedMeeting);
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
                .room(meeting.getRoom())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .organizerEmail(meeting.getOrganizerEmail())
                .status(meeting.getStatus())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .build();
    }
}
