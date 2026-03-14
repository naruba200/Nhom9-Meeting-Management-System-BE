package com.example.shopapp.controller;

import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.entity.User;
import com.example.shopapp.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @Valid @RequestBody CreateMeetingRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingResponse response = meetingService.createMeeting(request, organizer);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
