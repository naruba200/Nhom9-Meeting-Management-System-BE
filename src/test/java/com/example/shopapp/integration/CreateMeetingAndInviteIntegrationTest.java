package com.example.shopapp.integration;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.invitation.DeclineInvitationRequest;
import com.example.shopapp.dto.invitation.InvitationResponse;
import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.Notification;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.enums.NotificationType;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.NotificationRepository;
import com.example.shopapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for Create Meeting and Invite Attendees
 * Tests the full flow from meeting creation to invitation acceptance/decline
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - Create Meeting & Invite")
class CreateMeetingAndInviteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingAttendeeRepository meetingAttendeeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User organizer;
    private User attendee1;
    private User attendee2;
    private String organizerToken;
    private String attendee1Token;
    private String attendee2Token;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        notificationRepository.deleteAll();
        meetingAttendeeRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        // Create organizer
        organizer = User.builder()
                .email("organizer.invite@example.com")
                .fullName("Meeting Organizer Invite")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(organizer);

        // Create attendees
        attendee1 = User.builder()
                .email("attendee1.invite@example.com")
                .fullName("Attendee One Invite")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0909876543")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee1);

        attendee2 = User.builder()
                .email("attendee2.invite@example.com")
                .fullName("Attendee Two Invite")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0912345678")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee2);

        // Login to get tokens
        organizerToken = loginAndGetToken("organizer.invite@example.com");
        attendee1Token = loginAndGetToken("attendee1.invite@example.com");
        attendee2Token = loginAndGetToken("attendee2.invite@example.com");
    }

    private String loginAndGetToken(String email) throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("SecurePass123");

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        return authResponse.getToken();
    }

    @Nested
    @DisplayName("Create Meeting with Invitees Tests")
    class CreateMeetingWithInviteesTests {

        @Test
        @DisplayName("Should create meeting and send invitations successfully")
        void createMeeting_WithInvitees_Success() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Meeting with Invitees");
            request.setAgenda("Testing invitation flow");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of(
                    "attendee1.invite@example.com",
                    "attendee2.invite@example.com"
            ));
            request.setSyncWithGoogleCalendar(false);

            // Act
            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);

            // Assert - Meeting created
            assertThat(meetingResponse).isNotNull();
            assertThat(meetingResponse.getTitle()).isEqualTo("Meeting with Invitees");
            assertThat(meetingResponse.getAttendees()).hasSize(2);

            // Assert - Attendees saved with PENDING status
            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingResponse.getId());
            assertThat(attendees).hasSize(2);
            assertThat(attendees).allMatch(a -> a.getStatus() == InvitationStatus.PENDING);
            assertThat(attendees).extracting("email").containsExactlyInAnyOrder(
                    "attendee1.invite@example.com",
                    "attendee2.invite@example.com"
            );
        }

        @Test
        @DisplayName("Should create meeting without invitees successfully")
        void createMeeting_WithoutInvitees_Success() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Solo Meeting");
            request.setAgenda("No invitees");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act
            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);

            // Assert
            assertThat(meetingResponse).isNotNull();
            assertThat(meetingResponse.getAttendees()).isEmpty();

            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingResponse.getId());
            assertThat(attendees).isEmpty();
        }

        @Test
        @DisplayName("Should return 400 when inviting unregistered email")
        void createMeeting_UnregisteredEmail_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Invalid Invitee Meeting");
            request.setAgenda("Unregistered email");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("notregistered@example.com"));
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Verify no meeting was created
            List<Meeting> meetings = meetingRepository.findAll();
            assertThat(meetings).filteredOn("title", "Invalid Invitee Meeting").isEmpty();
        }

        @Test
        @DisplayName("Should return 400 when organizer tries to invite themselves")
        void createMeeting_OrganizerSelfInvite_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Self Invite Meeting");
            request.setAgenda("Organizer inviting self");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("organizer.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Verify no meeting was created
            List<Meeting> meetings = meetingRepository.findAll();
            assertThat(meetings).filteredOn("title", "Self Invite Meeting").isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Invitations Tests")
    class GetInvitationsTests {

        @Test
        @DisplayName("Should get weekly invitations for attendee")
        void getWeeklyInvitations_Success() throws Exception {
            // Arrange - Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Weekly Invitation Meeting");
            request.setAgenda("Testing weekly invitations");
            request.setStartTime(LocalDateTime.now().plusDays(2));
            request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Act & Assert
            mockMvc.perform(get("/api/invitations/weekly")
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].meetingTitle").value("Weekly Invitation Meeting"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("Should get invitation history for attendee")
        void getInvitationHistory_Success() throws Exception {
            // Arrange - Create 2 meetings with attendee
            for (int i = 1; i <= 2; i++) {
                CreateMeetingRequest request = new CreateMeetingRequest();
                request.setTitle("History Meeting " + i);
                request.setAgenda("Testing history");
                request.setStartTime(LocalDateTime.now().plusDays(i));
                request.setEndTime(LocalDateTime.now().plusDays(i).plusHours(1));
                request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
                request.setSyncWithGoogleCalendar(false);

                mockMvc.perform(post("/api/meetings")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            // Act & Assert
            mockMvc.perform(get("/api/invitations/history")
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Should return empty list when no invitations")
        void getInvitations_NoInvitations_ReturnsEmpty() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/invitations/weekly")
                    .header("Authorization", "Bearer " + attendee2Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Accept Invitation Tests")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Should accept invitation successfully")
        void acceptInvitation_Success() throws Exception {
            // Arrange - Create meeting and get attendee ID
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Accept Invitation Meeting");
            request.setAgenda("Testing accept");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);

            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            // Act
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Assert - Status changed to ACCEPTED
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(updatedAttendee.getRespondedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should return 400 when accepting already accepted invitation")
        void acceptInvitation_AlreadyAccepted_Returns400() throws Exception {
            // Arrange - Create meeting and accept
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Already Accepted Meeting");
            request.setAgenda("Testing double accept");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            // First accept
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Act & Assert - Second accept should fail
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when accepting already declined invitation")
        void acceptInvitation_AlreadyDeclined_Returns400() throws Exception {
            // Arrange - Create meeting and decline
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Already Declined Meeting");
            request.setAgenda("Testing accept after decline");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            // Decline first
            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("Not available");

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Act & Assert - Accept should fail
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Decline Invitation Tests")
    class DeclineInvitationTests {

        @Test
        @DisplayName("Should decline invitation with reason successfully")
        void declineInvitation_WithReason_Success() throws Exception {
            // Arrange - Create meeting and get attendee ID
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Decline Invitation Meeting");
            request.setAgenda("Testing decline");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("I have a scheduling conflict");

            // Act
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Assert - Status changed to DECLINED with reason
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee.getResponseReason()).isEqualTo("I have a scheduling conflict");
            assertThat(updatedAttendee.getRespondedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should return 400 when declining without reason")
        void declineInvitation_WithoutReason_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("No Reason Decline Meeting");
            request.setAgenda("Testing decline validation");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason(""); // Empty reason

            // Act & Assert
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when declining already declined invitation")
        void declineInvitation_AlreadyDeclined_Returns400() throws Exception {
            // Arrange - Create meeting and decline
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Already Declined Again Meeting");
            request.setAgenda("Testing double decline");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            // First decline
            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("Not available");

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Act & Assert - Second decline should fail
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("End-to-End Meeting Invitation Flow Tests")
    class EndToEndMeetingInvitationFlowTests {

        @Test
        @DisplayName("Should create meeting, send invites, accept and verify notification")
        void createMeeting_AcceptInvite_VerifyNotification_Success() throws Exception {
            // Step 1: Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Accept Meeting");
            request.setAgenda("Full flow test - accept");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            // Step 2: Attendee accepts invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Step 3: Verify attendee status
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);

            // Step 4: Verify notification created for organizer
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_ACCEPTED);
        }

        @Test
        @DisplayName("Should create meeting, send invites, decline and verify notification with reason")
        void createMeeting_DeclineInvite_VerifyNotification_Success() throws Exception {
            // Step 1: Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Decline Meeting");
            request.setAgenda("Full flow test - decline");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.invite@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "attendee1.invite@example.com").orElseThrow();

            // Step 2: Attendee declines invitation
            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("I will be on vacation");

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Step 3: Verify attendee status
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee.getResponseReason()).isEqualTo("I will be on vacation");

            // Step 4: Verify notification created for organizer with reason
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_DECLINED);
            assertThat(notifications.get(0).getMessage()).contains("I will be on vacation");
        }

        @Test
        @DisplayName("Should create meeting with multiple attendees and track responses")
        void createMeeting_MultipleAttendees_TrackResponses_Success() throws Exception {
            // Step 1: Create meeting with 2 attendees
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Multiple Attendees Meeting");
            request.setAgenda("Tracking multiple responses");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of(
                    "attendee1.invite@example.com",
                    "attendee2.invite@example.com"
            ));
            request.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);

            // Get attendees
            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingResponse.getId());
            MeetingAttendee attendee1Entity = attendees.stream()
                    .filter(a -> a.getEmail().equals("attendee1.invite@example.com"))
                    .findFirst()
                    .orElseThrow();
            MeetingAttendee attendee2Entity = attendees.stream()
                    .filter(a -> a.getEmail().equals("attendee2.invite@example.com"))
                    .findFirst()
                    .orElseThrow();

            // Step 2: First attendee accepts
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee1Entity.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Step 3: Second attendee declines
            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("Busy that day");

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee2Entity.getId())
                    .header("Authorization", "Bearer " + attendee2Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Step 4: Verify both responses
            MeetingAttendee updatedAttendee1 = meetingAttendeeRepository.findById(attendee1Entity.getId()).orElseThrow();
            MeetingAttendee updatedAttendee2 = meetingAttendeeRepository.findById(attendee2Entity.getId()).orElseThrow();

            assertThat(updatedAttendee1.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(updatedAttendee2.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee2.getResponseReason()).isEqualTo("Busy that day");

            // Step 5: Verify notifications for organizer
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).hasSize(2);
            assertThat(notifications).extracting("type").containsExactlyInAnyOrder(
                    NotificationType.MEETING_INVITATION_ACCEPTED,
                    NotificationType.MEETING_INVITATION_DECLINED
            );
        }
    }
}
