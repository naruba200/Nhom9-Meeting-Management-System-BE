package com.example.shopapp.service;

import com.example.shopapp.dto.invitation.DeclineInvitationRequest;
import com.example.shopapp.dto.invitation.InvitationResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final MeetingRepository meetingRepository;
    private final NotificationService notificationService;

    public List<InvitationResponse> getWeeklyInvitations(User attendeeUser) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now.plusDays(7);

        return getAllInvitationResponses(attendeeUser.getEmail())
                .stream()
                .filter(invitation -> invitation.getStatus() == InvitationStatus.PENDING)
                .filter(invitation -> !invitation.getMeetingStartTime().isBefore(now)
                        && !invitation.getMeetingStartTime().isAfter(end))
                .sorted(Comparator.comparing(InvitationResponse::getMeetingStartTime))
                .toList();
    }

    public List<InvitationResponse> getInvitationHistory(User attendeeUser) {
        return getAllInvitationResponses(attendeeUser.getEmail());
    }

    @Transactional
    public void acceptInvitation(Long attendeeId, User attendeeUser) {
        MeetingAttendee attendee = meetingAttendeeRepository.findByIdAndEmail(attendeeId, attendeeUser.getEmail())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lời mời"));

        if (attendee.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Lời mời này đã được phản hồi trước đó");
        }

        attendee.setStatus(InvitationStatus.ACCEPTED);
        attendee.setResponseReason(null);
        attendee.setRespondedAt(LocalDateTime.now());
        meetingAttendeeRepository.save(attendee);
    }

    @Transactional
    public void declineInvitation(Long attendeeId, DeclineInvitationRequest request, User attendeeUser) {
        MeetingAttendee attendee = meetingAttendeeRepository.findByIdAndEmail(attendeeId, attendeeUser.getEmail())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy lời mời"));

        if (attendee.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Lời mời này đã được phản hồi trước đó");
        }

        Meeting meeting = meetingRepository.findById(attendee.getMeetingId())
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        String reason = request.getReason().trim();
        attendee.setStatus(InvitationStatus.DECLINED);
        attendee.setResponseReason(reason);
        attendee.setRespondedAt(LocalDateTime.now());
        meetingAttendeeRepository.save(attendee);

        notificationService.createInvitationDeclinedNotification(meeting, attendeeUser.getEmail(), reason);
    }

    private List<InvitationResponse> getAllInvitationResponses(String attendeeEmail) {
        List<MeetingAttendee> attendees = meetingAttendeeRepository
                .findAllByEmailOrderByInvitedAtDesc(attendeeEmail.trim().toLowerCase());

        if (attendees.isEmpty()) {
            return List.of();
        }

        Set<Long> meetingIds = attendees.stream().map(MeetingAttendee::getMeetingId).collect(Collectors.toSet());
        Map<Long, Meeting> meetingMap = meetingRepository.findAllById(meetingIds).stream()
                .collect(Collectors.toMap(Meeting::getId, meeting -> meeting));

        List<InvitationResponse> responses = new ArrayList<>();
        for (MeetingAttendee attendee : attendees) {
            Meeting meeting = meetingMap.get(attendee.getMeetingId());
            if (meeting == null) {
                continue;
            }

            responses.add(InvitationResponse.builder()
                    .attendeeId(attendee.getId())
                    .meetingId(meeting.getId())
                    .meetingTitle(meeting.getTitle())
                    .organizerEmail(meeting.getOrganizerEmail())
                    .meetingStartTime(meeting.getStartTime())
                    .meetingEndTime(meeting.getEndTime())
                    .status(attendee.getStatus())
                    .responseReason(attendee.getResponseReason())
                    .invitedAt(attendee.getInvitedAt())
                    .respondedAt(attendee.getRespondedAt())
                    .build());
        }

        responses.sort(Comparator.comparing(InvitationResponse::getInvitedAt).reversed());
        return responses;
    }
}
