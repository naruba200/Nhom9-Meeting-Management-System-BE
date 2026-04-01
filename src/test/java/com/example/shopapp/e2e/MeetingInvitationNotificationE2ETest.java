package com.example.shopapp.e2e;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.Notification;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
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
 * E2E Tests for Meeting Invitation and Notification Flow
 * Tests the complete flow from creating meeting to sending notifications
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E Tests - Meeting Invitation & Notification")
class MeetingInvitationNotificationE2ETest {

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
                .email("e2e.organizer@example.com")
                .fullName("E2E Organizer")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(organizer);

        // Create attendees
        attendee1 = User.builder()
                .email("e2e.attendee1@example.com")
                .fullName("E2E Attendee One")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0909876543")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee1);

        attendee2 = User.builder()
                .email("e2e.attendee2@example.com")
                .fullName("E2E Attendee Two")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0912345678")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee2);

        // Login to get tokens
        organizerToken = loginAndGetToken("e2e.organizer@example.com");
        attendee1Token = loginAndGetToken("e2e.attendee1@example.com");
        attendee2Token = loginAndGetToken("e2e.attendee2@example.com");
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
    @DisplayName("Create Meeting and Send Invitation E2E")
    class CreateMeetingAndSendInvitationE2E {

        @Test
        @DisplayName("E2E: Create meeting with attendees and verify invitations sent")
        void e2e_CreateMeeting_WithAttendees_VerifyInvitations() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Team Meeting");
            request.setAgenda("Discussing project roadmap");
            request.setStartTime(LocalDateTime.now().plusDays(2));
            request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
            request.setAttendeeEmails(List.of(
                    "e2e.attendee1@example.com",
                    "e2e.attendee2@example.com"
            ));
            request.setSyncWithGoogleCalendar(false);

            // Act - Create meeting
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
            assertThat(meetingResponse.getId()).isNotNull();
            assertThat(meetingResponse.getTitle()).isEqualTo("E2E Team Meeting");
            assertThat(meetingResponse.getOrganizerEmail()).isEqualTo("e2e.organizer@example.com");
            assertThat(meetingResponse.getStatus().toString()).isEqualTo("SCHEDULED");

            // Assert - Attendees invited
            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingResponse.getId());
            assertThat(attendees).hasSize(2);
            assertThat(attendees).allMatch(a -> a.getStatus() == InvitationStatus.PENDING);
            assertThat(attendees).extracting("email").containsExactlyInAnyOrder(
                    "e2e.attendee1@example.com",
                    "e2e.attendee2@example.com"
            );

            // Assert - Attendees can see invitation in their list
            mockMvc.perform(get("/api/invitations/weekly")
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].meetingTitle").value("E2E Team Meeting"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("E2E: Create meeting without attendees - no invitations sent")
        void e2e_CreateMeeting_WithoutAttendees_NoInvitations() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Solo Meeting");
            request.setAgenda("Working alone");
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
            assertThat(meetingResponse.getAttendees()).isEmpty();

            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingResponse.getId());
            assertThat(attendees).isEmpty();
        }

        @Test
        @DisplayName("E2E: Create meeting and verify attendee cannot invite themselves")
        void e2e_CreateMeeting_AttendeeSelfInvite_Fails() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Self Invite Meeting");
            request.setAgenda("Trying to self invite");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("e2e.attendee1@example.com")); // Attendee trying to invite themselves
            request.setSyncWithGoogleCalendar(false);

            // Get attendee token
            String attendeeToken = loginAndGetToken("e2e.attendee1@example.com");

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Verify no meeting was created
            List<Meeting> meetings = meetingRepository.findAll();
            assertThat(meetings).filteredOn("title", "E2E Self Invite Meeting").isEmpty();
        }
    }

    @Nested
    @DisplayName("Invitation Response and Notification E2E")
    class InvitationResponseAndNotificationE2E {

        @Test
        @DisplayName("E2E: Attendee accepts invitation and organizer receives notification")
        void e2e_AcceptInvitation_OrganizerReceivesNotification() throws Exception {
            // Step 1: Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Accept Meeting");
            request.setAgenda("Testing accept flow");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("e2e.attendee1@example.com"));
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
                    meetingResponse.getId(), "e2e.attendee1@example.com").orElseThrow();

            // Step 2: Attendee accepts invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Step 3: Verify attendee status changed
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(updatedAttendee.getRespondedAt()).isNotNull();

            // Step 4: Verify organizer received notification
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_ACCEPTED);
            assertThat(notifications.get(0).getMessage()).contains("e2e.attendee1@example.com");
            assertThat(notifications.get(0).isRead()).isFalse();
        }

        @Test
        @DisplayName("E2E: Attendee declines invitation with reason and organizer receives notification")
        void e2e_DeclineInvitation_OrganizerReceivesNotification() throws Exception {
            // Step 1: Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Decline Meeting");
            request.setAgenda("Testing decline flow");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("e2e.attendee1@example.com"));
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
                    meetingResponse.getId(), "e2e.attendee1@example.com").orElseThrow();

            // Step 2: Attendee declines with reason
            String declineReason = "I have a conflict with another meeting";

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"" + declineReason + "\"}"))
                    .andExpect(status().isNoContent());

            // Step 3: Verify attendee status changed
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee.getResponseReason()).isEqualTo(declineReason);

            // Step 4: Verify organizer received notification with reason
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_DECLINED);
            assertThat(notifications.get(0).getMessage()).contains(declineReason);
        }

        @Test
        @DisplayName("E2E: Multiple attendees respond and organizer receives all notifications")
        void e2e_MultipleAttendeesRespond_AllNotificationsReceived() throws Exception {
            // Step 1: Create meeting with 2 attendees
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Multiple Attendees Meeting");
            request.setAgenda("Testing multiple responses");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of(
                    "e2e.attendee1@example.com",
                    "e2e.attendee2@example.com"
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
                    .filter(a -> a.getEmail().equals("e2e.attendee1@example.com"))
                    .findFirst()
                    .orElseThrow();
            MeetingAttendee attendee2Entity = attendees.stream()
                    .filter(a -> a.getEmail().equals("e2e.attendee2@example.com"))
                    .findFirst()
                    .orElseThrow();

            // Step 2: First attendee accepts
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee1Entity.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Step 3: Second attendee declines
            String declineReason = "Out of office that day";
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee2Entity.getId())
                    .header("Authorization", "Bearer " + attendee2Token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\": \"" + declineReason + "\"}"))
                    .andExpect(status().isNoContent());

            // Step 4: Verify both responses
            MeetingAttendee updatedAttendee1 = meetingAttendeeRepository.findById(attendee1Entity.getId()).orElseThrow();
            MeetingAttendee updatedAttendee2 = meetingAttendeeRepository.findById(attendee2Entity.getId()).orElseThrow();

            assertThat(updatedAttendee1.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(updatedAttendee2.getStatus()).isEqualTo(InvitationStatus.DECLINED);

            // Step 5: Verify organizer received both notifications
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).hasSize(2);
            assertThat(notifications).extracting("type").containsExactlyInAnyOrder(
                    NotificationType.MEETING_INVITATION_ACCEPTED,
                    NotificationType.MEETING_INVITATION_DECLINED
            );
        }
    }

    @Nested
    @DisplayName("Notification Management E2E")
    class NotificationManagementE2E {

        @Test
        @DisplayName("E2E: User can view unread notification count")
        void e2e_ViewUnreadNotificationCount() throws Exception {
            // Step 1: Create meeting and accept to generate notification
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Notification Count Meeting");
            request.setAgenda("Testing notification count");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("e2e.attendee1@example.com"));
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
                    meetingResponse.getId(), "e2e.attendee1@example.com").orElseThrow();

            // Step 2: Accept invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Step 3: Check unread count for organizer
            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1")); // 1 unread notification
        }

        @Test
        @DisplayName("E2E: User can mark all notifications as read")
        void e2e_MarkAllNotificationsAsRead() throws Exception {
            // Step 1: Create 2 meetings and accept both to generate notifications
            for (int i = 1; i <= 2; i++) {
                CreateMeetingRequest request = new CreateMeetingRequest();
                request.setTitle("E2E Mark Read Meeting " + i);
                request.setAgenda("Testing mark as read");
                request.setStartTime(LocalDateTime.now().plusDays(i));
                request.setEndTime(LocalDateTime.now().plusDays(i).plusHours(1));
                request.setAttendeeEmails(List.of("e2e.attendee1@example.com"));
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
                        meetingResponse.getId(), "e2e.attendee1@example.com").orElseThrow();

                // Accept to generate notification
                mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                        .header("Authorization", "Bearer " + attendee1Token))
                        .andExpect(status().isNoContent());
            }

            // Step 2: Verify unread count is 2
            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("2"));

            // Step 3: Mark all as read
            mockMvc.perform(post("/api/notifications/mark-all-read")
                    .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isNoContent());

            // Step 4: Verify unread count is 0
            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("0"));

            // Step 5: Verify all notifications are read in database
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    organizer.getEmail());
            assertThat(notifications).allMatch(Notification::isRead);
        }

        @Test
        @DisplayName("E2E: User can view all notifications")
        void e2e_ViewAllNotifications() throws Exception {
            // Step 1: Create meeting and accept to generate notification
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E View Notifications Meeting");
            request.setAgenda("Testing view notifications");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("e2e.attendee1@example.com"));
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
                    meetingResponse.getId(), "e2e.attendee1@example.com").orElseThrow();

            // Step 2: Accept invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendee1Token))
                    .andExpect(status().isNoContent());

            // Step 3: View all notifications
            mockMvc.perform(get("/api/notifications")
                    .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].type").value("MEETING_INVITATION_ACCEPTED"))
                    .andExpect(jsonPath("$[0].read").value(false))
                    .andExpect(jsonPath("$[0].title").exists());
        }
    }
}
