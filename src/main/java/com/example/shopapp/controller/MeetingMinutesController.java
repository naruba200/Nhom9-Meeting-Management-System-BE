package com.example.shopapp.controller;

import com.example.shopapp.dto.minutes.CreateMeetingMinutesRequest;
import com.example.shopapp.dto.minutes.MeetingMinutesResponse;
import com.example.shopapp.dto.minutes.SignMeetingMinutesRequest;
import com.example.shopapp.dto.minutes.UpdateMeetingMinutesRequest;
import com.example.shopapp.entity.User;
import com.example.shopapp.service.MeetingMinutesService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meeting-minutes")
@RequiredArgsConstructor
public class MeetingMinutesController {

    private final MeetingMinutesService minutesService;

    @GetMapping("/meeting/{meetingId}")
    public ResponseEntity<MeetingMinutesResponse> getMinutesByMeeting(
            @PathVariable Long meetingId,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        MeetingMinutesResponse response = minutesService.getMinutesByMeeting(meetingId, user);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{minutesId}")
    public ResponseEntity<MeetingMinutesResponse> getMinutesById(
            @PathVariable Long minutesId,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        MeetingMinutesResponse response = minutesService.getMinutesById(minutesId, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/meeting/{meetingId}")
    public ResponseEntity<MeetingMinutesResponse> createMinutes(
            @PathVariable Long meetingId,
            @Valid @RequestBody CreateMeetingMinutesRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingMinutesResponse response = minutesService.createMinutes(meetingId, request, organizer);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{minutesId}")
    public ResponseEntity<MeetingMinutesResponse> updateMinutes(
            @PathVariable Long minutesId,
            @Valid @RequestBody UpdateMeetingMinutesRequest request,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        MeetingMinutesResponse response = minutesService.updateMinutes(minutesId, request, organizer);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{minutesId}/sign")
    public ResponseEntity<MeetingMinutesResponse> signMinutes(
            @PathVariable Long minutesId,
            @Valid @RequestBody SignMeetingMinutesRequest request,
            Authentication authentication) {
        User signer = (User) authentication.getPrincipal();
        MeetingMinutesResponse response = minutesService.signMinutes(minutesId, request, signer);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{minutesId}/finalize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finalizeMinutes(
            @PathVariable Long minutesId,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        minutesService.finalizeMinutes(minutesId, organizer);
    }

    @DeleteMapping("/{minutesId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMinutes(
            @PathVariable Long minutesId,
            Authentication authentication) {
        User organizer = (User) authentication.getPrincipal();
        minutesService.deleteMinutes(minutesId, organizer);
    }
}
