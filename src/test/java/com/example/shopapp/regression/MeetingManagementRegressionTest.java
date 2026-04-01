package com.example.shopapp.regression;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.auth.RegisterRequest;
import com.example.shopapp.dto.invitation.DeclineInvitationRequest;
import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.dto.meeting.UpdateMeetingRequest;
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
import org.junit.jupiter.api.*;
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
 * Regression Test Suite for Meeting Management System
 * 
 * Purpose: Ensure existing functionality continues to work after new changes
 * Run this suite before deploying any changes to production
 * 
 * Coverage:
 * - User Registration & Authentication
 * - Meeting CRUD Operations
 * - Meeting Invitations
 * - Invitation Responses (Accept/Decline)
 * - Notifications
 * - Meeting Updates & Cancellation
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Regression Tests - Meeting Management System")
class MeetingManagementRegressionTest {

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

    private User testUser;
    private User attendeeUser;
    private String userToken;
    private String attendeeToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up data
        notificationRepository.deleteAll();
        meetingAttendeeRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser = User.builder()
                .email("regression.test@example.com")
                .fullName("Regression Test User")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(testUser);

        attendeeUser = User.builder()
                .email("regression.attendee@example.com")
                .fullName("Regression Attendee")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0909876543")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendeeUser);

        // Login to get tokens
        userToken = loginAndGetToken("regression.test@example.com");
        attendeeToken = loginAndGetToken("regression.attendee@example.com");
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
    @DisplayName("REG-001: User Authentication Flow")
    class AuthenticationRegressionTests {

        @Test
        @DisplayName("REG-001.1: User can login with valid credentials")
        void reg001_1_UserLogin_ValidCredentials_Success() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("regression.test@example.com");
            loginRequest.setPassword("SecurePass123");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("regression.test@example.com"))
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.fullName").value("Regression Test User"));
        }

        @Test
        @DisplayName("REG-001.2: User cannot login with invalid password")
        void reg001_2_UserLogin_InvalidPassword_Returns400() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("regression.test@example.com");
            loginRequest.setPassword("WrongPassword");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("REG-001.3: User cannot login with non-existent email")
        void reg001_3_UserLogin_NonExistentEmail_Returns400() throws Exception {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setEmail("nonexistent@example.com");
            loginRequest.setPassword("SecurePass123");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("REG-002: Meeting Creation Flow")
    class MeetingCreationRegressionTests {

        @Test
        @DisplayName("REG-002.1: User can create meeting without attendees")
        void reg002_1_CreateMeeting_NoAttendees_Success() throws Exception {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Regression Test Meeting");
            request.setAgenda("Testing meeting creation");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("Regression Test Meeting"))
                    .andExpect(jsonPath("$.organizerEmail").value("regression.test@example.com"))
                    .andExpect(jsonPath("$.status").value("SCHEDULED"));
        }

        @Test
        @DisplayName("REG-002.2: User can create meeting with attendees")
        void reg002_2_CreateMeeting_WithAttendees_Success() throws Exception {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Regression Test Meeting with Attendees");
            request.setAgenda("Testing meeting with attendees");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);

            // Verify attendees were invited
            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(meetingResponse.getId());
            assertThat(attendees).hasSize(1);
            assertThat(attendees.get(0).getEmail()).isEqualTo("regression.attendee@example.com");
            assertThat(attendees.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("REG-002.3: Cannot create meeting with end time before start time")
        void reg002_3_CreateMeeting_InvalidTime_Returns400() throws Exception {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Invalid Time Meeting");
            request.setAgenda("Bad time");
            request.setStartTime(LocalDateTime.now().plusDays(1).plusHours(2));
            request.setEndTime(LocalDateTime.now().plusDays(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("REG-002.4: Cannot create meeting with unregistered email")
        void reg002_4_CreateMeeting_UnregisteredEmail_Returns400() throws Exception {
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Unregistered Email Meeting");
            request.setAgenda("Testing");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("unregistered@example.com"));
            request.setSyncWithGoogleCalendar(false);

            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("REG-003: Meeting Invitation Flow")
    class MeetingInvitationRegressionTests {

        @Test
        @DisplayName("REG-003.1: Attendee can view weekly invitations")
        void reg003_1_ViewWeeklyInvitations_Success() throws Exception {
            // Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Weekly Invitation Test");
            request.setAgenda("Testing");
            request.setStartTime(LocalDateTime.now().plusDays(2));
            request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Attendee views weekly invitations
            mockMvc.perform(get("/api/invitations/weekly")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].meetingTitle").value("Weekly Invitation Test"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("REG-003.2: Attendee can view invitation history")
        void reg003_2_ViewInvitationHistory_Success() throws Exception {
            // Create meeting with attendee
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("History Test Meeting");
            request.setAgenda("Testing");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Attendee views history
            mockMvc.perform(get("/api/invitations/history")
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("REG-004: Invitation Response Flow")
    class InvitationResponseRegressionTests {

        @Test
        @DisplayName("REG-004.1: Attendee can accept invitation")
        void reg004_1_AcceptInvitation_Success() throws Exception {
            // Create meeting and get attendee ID
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Accept Test Meeting");
            request.setAgenda("Testing accept");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            // Accept invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Verify status changed
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("REG-004.2: Attendee can decline invitation with reason")
        void reg004_2_DeclineInvitation_Success() throws Exception {
            // Create meeting and get attendee ID
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Decline Test Meeting");
            request.setAgenda("Testing decline");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            // Decline invitation
            DeclineInvitationRequest declineRequest = new DeclineInvitationRequest();
            declineRequest.setReason("Not available");

            mockMvc.perform(post("/api/invitations/{attendeeId}/decline", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(declineRequest)))
                    .andExpect(status().isNoContent());

            // Verify status changed
            MeetingAttendee updatedAttendee = meetingAttendeeRepository.findById(attendee.getId()).orElseThrow();
            assertThat(updatedAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(updatedAttendee.getResponseReason()).isEqualTo("Not available");
        }

        @Test
        @DisplayName("REG-004.3: Cannot accept already responded invitation")
        void reg004_3_AcceptAlreadyResponded_Returns400() throws Exception {
            // Create meeting and accept
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Already Responded Test");
            request.setAgenda("Testing");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            // First accept
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Second accept should fail
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("REG-005: Notification Flow")
    class NotificationRegressionTests {

        @Test
        @DisplayName("REG-005.1: Organizer receives notification when attendee accepts")
        void reg005_1_OrganizerReceivesAcceptNotification_Success() throws Exception {
            // Create meeting and accept
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Notification Test Meeting");
            request.setAgenda("Testing notifications");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            // Accept invitation
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Verify notification created
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    testUser.getEmail());
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_ACCEPTED);
        }

        @Test
        @DisplayName("REG-005.2: User can view unread notification count")
        void reg005_2_ViewUnreadCount_Success() throws Exception {
            // Create meeting and accept to generate notification
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Unread Count Test");
            request.setAgenda("Testing");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            // Accept
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Check unread count
            mockMvc.perform(get("/api/notifications/unread-count")
                    .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));
        }

        @Test
        @DisplayName("REG-005.3: User can view all notifications")
        void reg005_3_ViewAllNotifications_Success() throws Exception {
            // Create meeting and accept
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("View Notifications Test");
            request.setAgenda("Testing");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("regression.attendee@example.com"));
            request.setSyncWithGoogleCalendar(false);

            String response = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(response, MeetingResponse.class);
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            // Accept
            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // View notifications
            mockMvc.perform(get("/api/notifications")
                    .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("REG-006: Meeting Update & Cancel Flow")
    class MeetingUpdateCancelRegressionTests {

        @Test
        @DisplayName("REG-006.1: Organizer can update meeting")
        void reg006_1_UpdateMeeting_Success() throws Exception {
            // Create meeting
            CreateMeetingRequest createRequest = new CreateMeetingRequest();
            createRequest.setTitle("Original Title");
            createRequest.setAgenda("Original agenda");
            createRequest.setStartTime(LocalDateTime.now().plusDays(1));
            createRequest.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            createRequest.setAttendeeEmails(null);
            createRequest.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);

            // Update meeting
            UpdateMeetingRequest updateRequest = new UpdateMeetingRequest();
            updateRequest.setTitle("Updated Title");
            updateRequest.setAgenda("Updated agenda");
            updateRequest.setStartTime(LocalDateTime.now().plusDays(2));
            updateRequest.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            updateRequest.setSyncWithGoogleCalendar(false);

            mockMvc.perform(put("/api/meetings/{meetingId}", meetingResponse.getId())
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        @DisplayName("REG-006.2: Organizer can cancel meeting")
        void reg006_2_CancelMeeting_Success() throws Exception {
            // Create meeting
            CreateMeetingRequest createRequest = new CreateMeetingRequest();
            createRequest.setTitle("Cancel Test Meeting");
            createRequest.setAgenda("Testing cancel");
            createRequest.setStartTime(LocalDateTime.now().plusDays(1));
            createRequest.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            createRequest.setAttendeeEmails(null);
            createRequest.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);

            // Cancel meeting
            mockMvc.perform(put("/api/meetings/{meetingId}/cancel", meetingResponse.getId())
                    .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            // Verify in database
            Meeting cancelledMeeting = meetingRepository.findById(meetingResponse.getId()).orElseThrow();
            assertThat(cancelledMeeting.getStatus()).isEqualTo(MeetingStatus.CANCELLED);
        }

        @Test
        @DisplayName("REG-006.3: Non-organizer cannot update meeting")
        void reg006_3_NonOrganizerCannotUpdate_Returns400() throws Exception {
            // Create meeting
            CreateMeetingRequest createRequest = new CreateMeetingRequest();
            createRequest.setTitle("Non-organizer Update Test");
            createRequest.setAgenda("Testing");
            createRequest.setStartTime(LocalDateTime.now().plusDays(1));
            createRequest.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            createRequest.setAttendeeEmails(null);
            createRequest.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);

            // Attendee tries to update
            UpdateMeetingRequest updateRequest = new UpdateMeetingRequest();
            updateRequest.setTitle("Hacked Title");
            updateRequest.setSyncWithGoogleCalendar(false);

            mockMvc.perform(put("/api/meetings/{meetingId}", meetingResponse.getId())
                    .header("Authorization", "Bearer " + attendeeToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("REG-007: Complete User Journey")
    class CompleteUserJourneyRegressionTests {

        @Test
        @DisplayName("REG-007.1: Complete meeting lifecycle - Create, Invite, Accept, Update, Cancel")
        void reg007_1_CompleteMeetingLifecycle_Success() throws Exception {
            // Step 1: Create meeting
            CreateMeetingRequest createRequest = new CreateMeetingRequest();
            createRequest.setTitle("Complete Lifecycle Meeting");
            createRequest.setAgenda("Testing complete flow");
            createRequest.setStartTime(LocalDateTime.now().plusDays(1));
            createRequest.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            createRequest.setAttendeeEmails(List.of("regression.attendee@example.com"));
            createRequest.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse meetingResponse = objectMapper.readValue(createResponse, MeetingResponse.class);
            assertThat(meetingResponse.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);

            // Step 2: Attendee accepts invitation
            MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(
                    meetingResponse.getId(), "regression.attendee@example.com").orElseThrow();

            mockMvc.perform(post("/api/invitations/{attendeeId}/accept", attendee.getId())
                    .header("Authorization", "Bearer " + attendeeToken))
                    .andExpect(status().isNoContent());

            // Step 3: Verify notification sent
            List<Notification> notifications = notificationRepository.findAllByRecipientEmailOrderByCreatedAtDesc(
                    testUser.getEmail());
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.MEETING_INVITATION_ACCEPTED);

            // Step 4: Update meeting
            UpdateMeetingRequest updateRequest = new UpdateMeetingRequest();
            updateRequest.setTitle("Updated Lifecycle Meeting");
            updateRequest.setStartTime(LocalDateTime.now().plusDays(2));
            updateRequest.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            updateRequest.setSyncWithGoogleCalendar(false);

            mockMvc.perform(put("/api/meetings/{meetingId}", meetingResponse.getId())
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk());

            // Step 5: Cancel meeting
            mockMvc.perform(put("/api/meetings/{meetingId}/cancel", meetingResponse.getId())
                    .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            // Verify final state
            Meeting finalMeeting = meetingRepository.findById(meetingResponse.getId()).orElseThrow();
            assertThat(finalMeeting.getStatus()).isEqualTo(MeetingStatus.CANCELLED);
            assertThat(finalMeeting.getTitle()).isEqualTo("Updated Lifecycle Meeting");
        }
    }
}
