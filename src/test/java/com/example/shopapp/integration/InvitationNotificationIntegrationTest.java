package com.example.shopapp.integration;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.invitation.DeclineInvitationRequest;
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
 * Integration tests for Notifications and Invitation Accept/Decline
 * Uses real database (H2) and full Spring context
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Integration Tests - Notifications & Invitation Accept/Decline")
class InvitationNotificationIntegrationTest {

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
    private User attendee;
    private String organizerToken;
    private String attendeeToken;
    private Meeting testMeeting;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        notificationRepository.deleteAll();
        meetingAttendeeRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        // Create organizer
        organizer = User.builder()
                .email("organizer@example.com")
                .fullName("Meeting Organizer")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(organizer);

        // Create attendee
        attendee = User.builder()
                .email("vi.duong059@gmail.com")
                .fullName("Vi Duong")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0909876543")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee);

        // Login both users
        organizerToken = loginAndGetToken("organizer@example.com");
        attendeeToken = loginAndGetToken("vi.duong059@gmail.com");

        // Create a meeting
        testMeeting = createMeeting("Test Meeting", organizerToken);
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

    private Meeting createMeeting(String title, String token) throws Exception {
        CreateMeetingRequest request = new CreateMeetingRequest();
        request.setTitle(title);
        request.setAgenda("Test Agenda");
        request.setStartTime(LocalDateTime.now().plusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        request.setAttendeeEmails(List.of("vi.duong059@gmail.com"));
        request.setSyncWithGoogleCalendar(false); // Required field

        String response = mockMvc.perform(post("/api/meetings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // 201 Created
                .andReturn()
                .getResponse()
                .getContentAsString();

        MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
        return meetingRepository.findById(meetingResponse.getId()).orElseThrow();
    }

    @Nested
    @DisplayName("Notification Integration Tests")
    class NotificationIntegrationTests {

        @Test
        @DisplayName("Should get notifications for authenticated user")
        void getNotifications_WhenAuthenticated_Success() throws Exception {
            // Create a notification
            Notification notification = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Test Notification")
                    .message("Test Message")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(false)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);

            // Act & Assert
            mockMvc.perform(get("/api/notifications")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Test Notification"))
                    .andExpect(jsonPath("$[0].read").value(false));
        }

        @Test
        @DisplayName("Should get unread count for authenticated user")
        void getUnreadCount_WhenAuthenticated_Success() throws Exception {
            // Create notifications
            Notification unread1 = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Unread 1")
                    .message("Message 1")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(false)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();

            Notification unread2 = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Unread 2")
                    .message("Message 2")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(false)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();

            Notification read1 = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Read 1")
                    .message("Message 1")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(true)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.saveAll(List.of(unread1, unread2, read1));

            // Act & Assert
            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("2")); // 2 unread
        }

        @Test
        @DisplayName("Should mark all notifications as read")
        void markAllAsRead_WhenAuthenticated_Success() throws Exception {
            // Create unread notifications
            Notification unread1 = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Unread 1")
                    .message("Message 1")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(false)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();

            Notification unread2 = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Unread 2")
                    .message("Message 2")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(false)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.saveAll(List.of(unread1, unread2));

            // Act
            mockMvc.perform(post("/api/notifications/mark-all-read")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Assert - Verify in database
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(attendee.getEmail());
            assertThat(notifications).allMatch(Notification::isRead);
        }

        @Test
        @DisplayName("Should mark single notification as read")
        void markAsRead_WhenAuthenticated_Success() throws Exception {
            // Create unread notification
            Notification unread = Notification.builder()
                    .recipientEmail(attendee.getEmail())
                    .title("Unread")
                    .message("Message")
                    .type(NotificationType.MEETING_INVITATION)
                    .isRead(false)
                    .meetingId(testMeeting.getId())
                    .createdAt(LocalDateTime.now())
                    .build();

            Notification savedNotification = notificationRepository.save(unread);

            // Act
            mockMvc.perform(post("/api/notifications/{notificationId}/read", savedNotification.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Assert - Verify in database
            Notification updatedNotification = notificationRepository.findById(savedNotification.getId()).orElseThrow();
            assertThat(updatedNotification.isRead()).isTrue();
        }

        @Test
        @DisplayName("Should return 404 when marking non-existent notification as read")
        void markAsRead_NotFound_Returns404() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/notifications/999/read")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Invitation Accept/Decline Integration Tests")
    class InvitationAcceptDeclineTests {

        @Test
        @DisplayName("Should accept invitation successfully")
        void acceptInvitation_Success() throws Exception {
            // Create meeting attendee (invitation)
            MeetingAttendee attendeeEntity = MeetingAttendee.builder()
                    .meetingId(testMeeting.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.PENDING)
                    .invitedAt(LocalDateTime.now())
                    .build();
            meetingAttendeeRepository.save(attendeeEntity);

            // Act
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Assert - Verify in database
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendeeEntity.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(updatedAttendee.getRespondedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should decline invitation successfully with reason")
        void declineInvitation_Success() throws Exception {
            // Create meeting attendee (invitation)
            MeetingAttendee attendeeEntity = MeetingAttendee.builder()
                    .meetingId(testMeeting.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.PENDING)
                    .invitedAt(LocalDateTime.now())
                    .build();
            meetingAttendeeRepository.save(attendeeEntity);

            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("I have a scheduling conflict");

            // Act
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNoContent());

            // Assert - Verify in database
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendeeEntity.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee.getResponseReason()).isEqualTo("I have a scheduling conflict");
            assertThat(updatedAttendee.getRespondedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should return 400 when declining without reason")
        void declineInvitation_WithoutReason_Returns400() throws Exception {
            // Create meeting attendee (invitation)
            MeetingAttendee attendeeEntity = MeetingAttendee.builder()
                    .meetingId(testMeeting.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.PENDING)
                    .invitedAt(LocalDateTime.now())
                    .build();
            meetingAttendeeRepository.save(attendeeEntity);

            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason(""); // Empty reason

            // Act & Assert
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when accepting already responded invitation")
        void acceptInvitation_AlreadyResponded_Returns400() throws Exception {
            // Create meeting attendee with ACCEPTED status
            MeetingAttendee attendeeEntity = MeetingAttendee.builder()
                    .meetingId(testMeeting.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.ACCEPTED)
                    .invitedAt(LocalDateTime.now())
                    .respondedAt(LocalDateTime.now())
                    .build();
            meetingAttendeeRepository.save(attendeeEntity);

            // Act & Assert
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when declining already responded invitation")
        void declineInvitation_AlreadyResponded_Returns400() throws Exception {
            // Create meeting attendee with DECLINED status
            MeetingAttendee attendeeEntity = MeetingAttendee.builder()
                    .meetingId(testMeeting.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.DECLINED)
                    .invitedAt(LocalDateTime.now())
                    .respondedAt(LocalDateTime.now())
                    .build();
            meetingAttendeeRepository.save(attendeeEntity);

            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("Changed mind");

            // Act & Assert
            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should get weekly invitations for authenticated user")
        void getWeeklyInvitations_WhenAuthenticated_Success() throws Exception {
            // Create meeting in the future (within 7 days)
            Meeting futureMeeting = Meeting.builder()
                    .title("Future Meeting")
                    .agenda("Future Agenda")
                    .startTime(LocalDateTime.now().plusDays(2))
                    .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                    .organizerEmail(organizer.getEmail())
                    .room("Online")
                    .status(MeetingStatus.SCHEDULED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            meetingRepository.save(futureMeeting);

            // Create pending invitation
            MeetingAttendee attendeeEntity = MeetingAttendee.builder()
                    .meetingId(futureMeeting.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.PENDING)
                    .invitedAt(LocalDateTime.now())
                    .build();
            meetingAttendeeRepository.save(attendeeEntity);

            // Act & Assert
            mockMvc.perform(get("/api/invitations/weekly")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].meetingTitle").value("Future Meeting"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("Should get invitation history for authenticated user")
        void getInvitationHistory_WhenAuthenticated_Success() throws Exception {
            // Create meetings
            Meeting meeting1 = Meeting.builder()
                    .title("Meeting 1")
                    .agenda("Agenda 1")
                    .startTime(LocalDateTime.now().plusDays(1))
                    .endTime(LocalDateTime.now().plusDays(1).plusHours(1))
                    .organizerEmail(organizer.getEmail())
                    .room("Online")
                    .status(MeetingStatus.SCHEDULED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            meetingRepository.save(meeting1);

            Meeting meeting2 = Meeting.builder()
                    .title("Meeting 2")
                    .agenda("Agenda 2")
                    .startTime(LocalDateTime.now().plusDays(3))
                    .endTime(LocalDateTime.now().plusDays(3).plusHours(1))
                    .organizerEmail(organizer.getEmail())
                    .room("Online")
                    .status(MeetingStatus.SCHEDULED)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            meetingRepository.save(meeting2);

            // Create invitations with different statuses
            MeetingAttendee attendee1 = MeetingAttendee.builder()
                    .meetingId(meeting1.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.PENDING)
                    .invitedAt(LocalDateTime.now())
                    .build();

            MeetingAttendee attendee2 = MeetingAttendee.builder()
                    .meetingId(meeting2.getId())
                    .email(attendee.getEmail())
                    .status(InvitationStatus.ACCEPTED)
                    .invitedAt(LocalDateTime.now().minusDays(1))
                    .respondedAt(LocalDateTime.now())
                    .build();

            meetingAttendeeRepository.saveAll(List.of(attendee1, attendee2));

            // Act & Assert
            mockMvc.perform(get("/api/invitations/history")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].meetingTitle").value("Meeting 2")) // Most recent first
                    .andExpect(jsonPath("$[1].meetingTitle").value("Meeting 1"));
        }
    }

    @Nested
    @DisplayName("End-to-End Flow Tests")
    class EndToEndFlowTests {

        @Test
        @DisplayName("Should create meeting, send invitation, accept and verify notification")
        void createMeeting_AcceptInvitation_VerifyNotification_Success() throws Exception {
            // Step 1: Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Important Meeting");
            request.setAgenda("Discuss project");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("vi.duong059@gmail.com"));
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

            // Step 2: Get attendee entity
            MeetingAttendee attendeeEntity = meetingAttendeeRepository
                    .findByMeetingIdAndEmail(meetingResponse.getId(), attendee.getEmail())
                    .orElseThrow();

            // Step 3: Accept invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Step 4: Verify attendee status changed
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendeeEntity.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);

            // Step 5: Verify notification was created for organizer
            List<Notification> notifications = notificationRepository
                    .findAllByRecipientEmailOrderByCreatedAtDesc(organizer.getEmail());

            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_ACCEPTED);
        }

        @Test
        @DisplayName("Should create meeting, decline invitation with reason and verify notification")
        void createMeeting_DeclineInvitation_VerifyNotification_Success() throws Exception {
            // Step 1: Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Team Meeting");
            request.setAgenda("Team sync");
            request.setStartTime(LocalDateTime.now().plusDays(2));
            request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            request.setAttendeeEmails(List.of("vi.duong059@gmail.com"));
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

            // Step 2: Get attendee entity
            MeetingAttendee attendeeEntity = meetingAttendeeRepository
                    .findByMeetingIdAndEmail(meetingResponse.getId(), attendee.getEmail())
                    .orElseThrow();

            // Step 3: Decline invitation
            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("I will be on vacation");

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendeeEntity.getId())
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Step 4: Verify attendee status changed
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendeeEntity.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee.getResponseReason()).isEqualTo("I will be on vacation");

            // Step 5: Verify notification was created for organizer
            List<Notification> notifications = notificationRepository
                    .findAllByRecipientEmailOrderByCreatedAtDesc(organizer.getEmail());
            
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_DECLINED);
            assertThat(notifications.get(0).getMessage()).contains("I will be on vacation");
        }
    }
}
