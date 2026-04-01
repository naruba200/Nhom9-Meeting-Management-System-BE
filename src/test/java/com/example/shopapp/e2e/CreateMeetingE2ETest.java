package com.example.shopapp.e2e;

import com.example.shopapp.dto.auth.AuthResponse;
import com.example.shopapp.dto.auth.LoginRequest;
import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
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
 * E2E Tests for Meeting Creation
 * Tests the full flow from registration to meeting creation
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("E2E Tests - Create Meeting")
class CreateMeetingE2ETest {

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
    private PasswordEncoder passwordEncoder;

    private User organizer;
    private User attendee1;
    private User attendee2;
    private String organizerToken;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        meetingAttendeeRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        // Create organizer
        organizer = User.builder()
                .email("organizer.e2e@example.com")
                .fullName("Meeting Organizer E2E")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0901234567")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(organizer);

        // Create attendees
        attendee1 = User.builder()
                .email("attendee1.e2e@example.com")
                .fullName("Attendee One E2E")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0909876543")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee1);

        attendee2 = User.builder()
                .email("attendee2.e2e@example.com")
                .fullName("Attendee Two E2E")
                .password(passwordEncoder.encode("SecurePass123"))
                .phone("0912345678")
                .enabled(true)
                .role("USER")
                .build();
        userRepository.save(attendee2);

        // Login organizer to get token
        organizerToken = loginAndGetToken("organizer.e2e@example.com");
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
    @DisplayName("Create Meeting E2E Tests")
    class CreateMeetingE2ETests {

        @Test
        @DisplayName("Should create meeting with attendees successfully")
        void createMeeting_WithAttendees_Success() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("E2E Test Meeting");
            request.setAgenda("Testing end-to-end meeting creation");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("attendee1.e2e@example.com", "attendee2.e2e@example.com"));
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
            assertThat(meetingResponse.getTitle()).isEqualTo("E2E Test Meeting");
            assertThat(meetingResponse.getOrganizerEmail()).isEqualTo("organizer.e2e@example.com");
            assertThat(meetingResponse.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);
            assertThat(meetingResponse.getAttendees()).hasSize(2);

            // Verify in database
            Meeting savedMeeting = meetingRepository.findById(meetingResponse.getId()).orElseThrow();
            assertThat(savedMeeting.getTitle()).isEqualTo("E2E Test Meeting");
            assertThat(savedMeeting.getOrganizerEmail()).isEqualTo("organizer.e2e@example.com");

            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(savedMeeting.getId());
            assertThat(attendees).hasSize(2);
            assertThat(attendees).extracting("email").containsExactlyInAnyOrder(
                    "attendee1.e2e@example.com",
                    "attendee2.e2e@example.com"
            );
            assertThat(attendees).allMatch(a -> a.getStatus() == InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("Should create meeting without attendees successfully")
        void createMeeting_WithoutAttendees_Success() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Solo E2E Meeting");
            request.setAgenda("Meeting alone");
            request.setStartTime(LocalDateTime.now().plusDays(2));
            request.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
            request.setAttendeeEmails(null); // No attendees
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
            assertThat(meetingResponse.getTitle()).isEqualTo("Solo E2E Meeting");
            assertThat(meetingResponse.getAttendees()).isEmpty();

            // Verify in database
            Meeting savedMeeting = meetingRepository.findById(meetingResponse.getId()).orElseThrow();
            List<MeetingAttendee> attendees = meetingAttendeeRepository.findAllByMeetingId(savedMeeting.getId());
            assertThat(attendees).isEmpty();
        }

        @Test
        @DisplayName("Should create meeting with empty attendee list")
        void createMeeting_EmptyAttendeeList_Success() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Empty Attendees Meeting");
            request.setAgenda("No attendees");
            request.setStartTime(LocalDateTime.now().plusDays(3));
            request.setEndTime(LocalDateTime.now().plusDays(3).plusHours(1));
            request.setAttendeeEmails(List.of()); // Empty list
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
            assertThat(meetingResponse.getTitle()).isEqualTo("Empty Attendees Meeting");
            assertThat(meetingResponse.getAttendees()).isEmpty();
        }

        @Test
        @DisplayName("Should return 400 when end time is before start time")
        void createMeeting_InvalidTime_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Invalid Time Meeting");
            request.setAgenda("Bad time");
            request.setStartTime(LocalDateTime.now().plusDays(1).plusHours(2));
            request.setEndTime(LocalDateTime.now().plusDays(1)); // End before start
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            // Verify no meeting was created
            List<Meeting> meetings = meetingRepository.findAll();
            assertThat(meetings).filteredOn("title", "Invalid Time Meeting").isEmpty();
        }

        @Test
        @DisplayName("Should return 400 when organizer tries to invite themselves")
        void createMeeting_OrganizerInvitesSelf_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Self Invite Meeting");
            request.setAgenda("Inviting self");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(List.of("organizer.e2e@example.com")); // Organizer's email
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

        @Test
        @DisplayName("Should return 400 when inviting unregistered email")
        void createMeeting_UnregisteredEmail_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Unregistered Email Meeting");
            request.setAgenda("Inviting stranger");
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
            assertThat(meetings).filteredOn("title", "Unregistered Email Meeting").isEmpty();
        }

        @Test
        @DisplayName("Should return 403 when not authenticated")
        void createMeeting_NotAuthenticated_Returns403() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("No Auth Meeting");
            request.setAgenda("No token");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should trim meeting title before saving")
        void createMeeting_TrimTitle_Success() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("  Meeting with spaces  ");
            request.setAgenda("Testing trim");
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
            assertThat(meetingResponse.getTitle()).isEqualTo("Meeting with spaces");

            // Verify in database
            Meeting savedMeeting = meetingRepository.findById(meetingResponse.getId()).orElseThrow();
            assertThat(savedMeeting.getTitle()).isEqualTo("Meeting with spaces");
        }

        @Test
        @DisplayName("Should create multiple meetings successfully")
        void createMultipleMeetings_Success() throws Exception {
            // Arrange & Act - Create 3 meetings
            for (int i = 1; i <= 3; i++) {
                CreateMeetingRequest request = new CreateMeetingRequest();
                request.setTitle("E2E Meeting " + i);
                request.setAgenda("Meeting number " + i);
                request.setStartTime(LocalDateTime.now().plusDays(i));
                request.setEndTime(LocalDateTime.now().plusDays(i).plusHours(1));
                request.setAttendeeEmails(i % 2 == 0 ? List.of("attendee1.e2e@example.com") : null);
                request.setSyncWithGoogleCalendar(false);

                mockMvc.perform(post("/api/meetings")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            // Assert
            List<Meeting> meetings = meetingRepository.findAll();
            List<Meeting> e2eMeetings = meetings.stream()
                    .filter(m -> m.getTitle().startsWith("E2E Meeting"))
                    .toList();

            assertThat(e2eMeetings).hasSize(3);
            assertThat(e2eMeetings).extracting("title").containsExactlyInAnyOrder(
                    "E2E Meeting 1",
                    "E2E Meeting 2",
                    "E2E Meeting 3"
            );
        }
    }

    @Nested
    @DisplayName("Create Meeting with Validation Tests")
    class CreateMeetingValidationTests {

        @Test
        @DisplayName("Should return 400 when title is blank")
        void createMeeting_BlankTitle_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("   ");
            request.setAgenda("No title");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when title exceeds max length")
        void createMeeting_LongTitle_Returns400() throws Exception {
            // Arrange
            String longTitle = "A".repeat(256); // Assuming max is 255
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle(longTitle);
            request.setAgenda("Too long title");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when start time is null")
        void createMeeting_NullStartTime_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Null Start Time");
            request.setAgenda("No start");
            request.setStartTime(null);
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when end time is null")
        void createMeeting_NullEndTime_Returns400() throws Exception {
            // Arrange
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Null End Time");
            request.setAgenda("No end");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(null);
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            // Act & Assert
            mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Get Meetings E2E Tests")
    class GetMeetingsE2ETests {

        @Test
        @DisplayName("Should get paginated meetings for organizer")
        void getMeetings_ForOrganizer_Success() throws Exception {
            // Arrange - Create 2 meetings first
            for (int i = 1; i <= 2; i++) {
                CreateMeetingRequest request = new CreateMeetingRequest();
                request.setTitle("Get Meeting " + i);
                request.setAgenda("For pagination");
                request.setStartTime(LocalDateTime.now().plusDays(i));
                request.setEndTime(LocalDateTime.now().plusDays(i).plusHours(1));
                request.setAttendeeEmails(null);
                request.setSyncWithGoogleCalendar(false);

                mockMvc.perform(post("/api/meetings")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated());
            }

            // Act
            mockMvc.perform(get("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .param("page", "0")
                    .param("size", "10")
                    .param("sortOrder", "newest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("Should get meeting by ID")
        void getMeetingById_Success() throws Exception {
            // Arrange - Create a meeting first
            CreateMeetingRequest request = new CreateMeetingRequest();
            request.setTitle("Get By ID Meeting");
            request.setAgenda("Testing get by ID");
            request.setStartTime(LocalDateTime.now().plusDays(1));
            request.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
            request.setAttendeeEmails(null);
            request.setSyncWithGoogleCalendar(false);

            String createResponse = mockMvc.perform(post("/api/meetings")
                    .header("Authorization", "Bearer " + organizerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            MeetingResponse createdMeeting = objectMapper.readValue(createResponse, MeetingResponse.class);

            // Act
            mockMvc.perform(get("/api/meetings/{meetingId}", createdMeeting.getId())
                    .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(createdMeeting.getId()))
                    .andExpect(jsonPath("$.title").value("Get By ID Meeting"));
        }
    }
}
