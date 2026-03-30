package com.example.shopapp.controller;

import com.example.shopapp.dto.invitation.DeclineInvitationRequest;
import com.example.shopapp.dto.invitation.InvitationResponse;
import com.example.shopapp.entity.User;
import com.example.shopapp.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @GetMapping("/weekly")
    public List<InvitationResponse> getWeeklyInvitations(Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        return invitationService.getWeeklyInvitations(attendee);
    }

    @GetMapping("/history")
    public List<InvitationResponse> getInvitationHistory(Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        return invitationService.getInvitationHistory(attendee);
    }

    @PostMapping("/{attendeeId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptInvitation(@PathVariable Long attendeeId, Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        invitationService.acceptInvitation(attendeeId, attendee);
    }

    @PostMapping("/{attendeeId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineInvitation(
            @PathVariable Long attendeeId,
            @Valid @RequestBody DeclineInvitationRequest request,
            Authentication authentication) {
        User attendee = (User) authentication.getPrincipal();
        invitationService.declineInvitation(attendeeId, request, attendee);
    }
}
