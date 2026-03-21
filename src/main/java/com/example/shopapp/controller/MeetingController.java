package com.example.shopapp.controller;

import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.InviteMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.dto.meeting.UpdateMeetingAgendaRequest;
import com.example.shopapp.dto.meeting.UpdateMeetingRequest;
import com.example.shopapp.entity.User;
import com.example.shopapp.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<MeetingResponse> response = meetingService.getMeetingsForUser(user);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingResponse response = meetingService.createMeeting(request, organizer);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{meetingId}")
    public ResponseEntity<MeetingResponse> updateMeeting(
            @PathVariable Long meetingId,
            @Valid @RequestBody UpdateMeetingRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingResponse response = meetingService.updateMeeting(meetingId, request, organizer);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{meetingId}/agenda")
    public ResponseEntity<MeetingResponse> updateMeetingAgenda(
            @PathVariable Long meetingId,
            @Valid @RequestBody UpdateMeetingAgendaRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingResponse response = meetingService.updateMeetingAgenda(meetingId, request, organizer);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{meetingId}/cancel")
    public ResponseEntity<MeetingResponse> cancelMeeting(
            @PathVariable Long meetingId,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingResponse response = meetingService.cancelMeeting(meetingId, organizer);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{meetingId}/invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inviteAttendees(
            @PathVariable Long meetingId,
            @Valid @RequestBody InviteMeetingRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        meetingService.inviteAttendees(meetingId, request, organizer);
    }

    @DeleteMapping("/{meetingId}/attendees")
    public ResponseEntity<MeetingResponse> removeAttendee(
            @PathVariable Long meetingId,
            @RequestParam String attendeeEmail,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingResponse response = meetingService.removeAttendee(meetingId, attendeeEmail, organizer);
        return ResponseEntity.ok(response);
    }
}
