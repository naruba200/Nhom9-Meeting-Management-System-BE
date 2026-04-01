package com.example.shopapp.service;

import com.example.shopapp.dto.invitation.DeclineInvitationRequest;
import com.example.shopapp.dto.invitation.InvitationResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvitationService
 * Tests cover: accept/decline invitations, weekly invitations, invitation history
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationService Unit Tests")
class InvitationServiceTest {

    @Mock
    private MeetingAttendeeRepository meetingAttendeeRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private InvitationService invitationService;

    private User testUser;
    private Meeting testMeeting;
    private MeetingAttendee testAttendee;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");

        testMeeting = new Meeting();
        testMeeting.setId(1L);
        testMeeting.setTitle("Test Meeting");
        testMeeting.setOrganizerEmail("organizer@example.com");
        testMeeting.setStartTime(now.plusDays(1));
        testMeeting.setEndTime(now.plusDays(1).plusHours(1));
        testMeeting.setStatus(MeetingStatus.SCHEDULED);

        testAttendee = new MeetingAttendee();
        testAttendee.setId(100L);
        testAttendee.setMeetingId(1L);
        testAttendee.setEmail("test@example.com");
        testAttendee.setStatus(InvitationStatus.PENDING);
        testAttendee.setInvitedAt(now.minusDays(2));
    }

    @Nested
    @DisplayName("Accept Invitation Tests")
    class AcceptInvitationTests {

        @Test
        @DisplayName("Should accept invitation successfully when attendee exists and status is PENDING")
        void acceptInvitation_Success() {
            // Arrange
            Long attendeeId = 100L;
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.of(testMeeting));
            given(meetingAttendeeRepository.save(any(MeetingAttendee.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // Act
            invitationService.acceptInvitation(attendeeId, testUser);

            // Assert
            then(meetingAttendeeRepository).should().findByIdAndEmail(attendeeId, testUser.getEmail());
            then(meetingRepository).should().findById(testAttendee.getMeetingId());
            then(meetingAttendeeRepository).should().save(any(MeetingAttendee.class));
            then(notificationService).should()
                    .createInvitationAcceptedNotification(eq(testMeeting), eq(testUser.getEmail()));

            // Verify attendee status changed to ACCEPTED
            assertThat(testAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(testAttendee.getResponseReason()).isNull();
            assertThat(testAttendee.getRespondedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw BadRequestException when attendee not found")
        void acceptInvitation_AttendeeNotFound() {
            // Arrange
            Long attendeeId = 999L;
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.empty());

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.acceptInvitation(attendeeId, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Không tìm thấy lời mời");
            then(meetingAttendeeRepository).should().findByIdAndEmail(attendeeId, testUser.getEmail());
            then(meetingRepository).should(never()).findById(anyLong());
            then(notificationService).should(never()).createInvitationAcceptedNotification(any(), any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when meeting not found")
        void acceptInvitation_MeetingNotFound() {
            // Arrange
            Long attendeeId = 100L;
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.empty());

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.acceptInvitation(attendeeId, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Không tìm thấy cuộc họp");
            then(notificationService).should(never()).createInvitationAcceptedNotification(any(), any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when invitation already responded")
        void acceptInvitation_AlreadyResponded() {
            // Arrange
            Long attendeeId = 100L;
            testAttendee.setStatus(InvitationStatus.ACCEPTED); // Already accepted
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.acceptInvitation(attendeeId, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Lời mời này đã được phản hồi trước đó");
            then(meetingRepository).should(never()).findById(anyLong());
            then(notificationService).should(never()).createInvitationAcceptedNotification(any(), any());
        }

        @Test
        @DisplayName("Should throw BadRequestException when invitation was declined")
        void acceptInvitation_PreviouslyDeclined() {
            // Arrange
            Long attendeeId = 100L;
            testAttendee.setStatus(InvitationStatus.DECLINED); // Previously declined
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.acceptInvitation(attendeeId, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Lời mời này đã được phản hồi trước đó");
        }
    }

    @Nested
    @DisplayName("Decline Invitation Tests")
    class DeclineInvitationTests {

        @Test
        @DisplayName("Should decline invitation successfully with reason")
        void declineInvitation_Success() {
            // Arrange
            Long attendeeId = 100L;
            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("I have a conflict at that time");
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.of(testMeeting));
            given(meetingAttendeeRepository.save(any(MeetingAttendee.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // Act
            invitationService.declineInvitation(attendeeId, request, testUser);

            // Assert
            then(meetingAttendeeRepository).should().findByIdAndEmail(attendeeId, testUser.getEmail());
            then(meetingRepository).should().findById(testAttendee.getMeetingId());
            then(meetingAttendeeRepository).should().save(any(MeetingAttendee.class));
            then(notificationService).should()
                    .createInvitationDeclinedNotification(eq(testMeeting), eq(testUser.getEmail()), anyString());

            // Verify attendee status changed to DECLINED
            assertThat(testAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(testAttendee.getResponseReason()).isEqualTo("I have a conflict at that time");
            assertThat(testAttendee.getRespondedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should trim whitespace from decline reason")
        void declineInvitation_TrimReason() {
            // Arrange
            Long attendeeId = 100L;
            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("   Too busy   ");
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.of(testMeeting));
            given(meetingAttendeeRepository.save(any(MeetingAttendee.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // Act
            invitationService.declineInvitation(attendeeId, request, testUser);

            // Assert
            assertThat(testAttendee.getResponseReason()).isEqualTo("Too busy");
        }

        @Test
        @DisplayName("Should throw BadRequestException when attendee not found")
        void declineInvitation_AttendeeNotFound() {
            // Arrange
            Long attendeeId = 999L;
            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("Not available");
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.empty());

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.declineInvitation(attendeeId, request, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Không tìm thấy lời mời");
        }

        @Test
        @DisplayName("Should throw BadRequestException when invitation already responded")
        void declineInvitation_AlreadyResponded() {
            // Arrange
            Long attendeeId = 100L;
            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("Not available");
            testAttendee.setStatus(InvitationStatus.ACCEPTED);
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));

            // Act & Assert
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.declineInvitation(attendeeId, request, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Lời mời này đã được phản hồi trước đó");
        }
    }

    @Nested
    @DisplayName("Weekly Invitations Tests")
    class WeeklyInvitationsTests {

        @Test
        @DisplayName("Should return pending invitations within next 7 days sorted by start time")
        void getWeeklyInvitations_Success() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();
            
            // Create 3 invitations: 1 pending (within 7 days), 1 pending (after 7 days), 1 accepted
            MeetingAttendee pendingSoon = new MeetingAttendee();
            pendingSoon.setId(1L);
            pendingSoon.setMeetingId(1L);
            pendingSoon.setEmail(testUser.getEmail());
            pendingSoon.setStatus(InvitationStatus.PENDING);
            pendingSoon.setInvitedAt(now.minusDays(1));

            Meeting meetingSoon = new Meeting();
            meetingSoon.setId(1L);
            meetingSoon.setTitle("Meeting Tomorrow");
            meetingSoon.setOrganizerEmail("org1@example.com");
            meetingSoon.setStartTime(now.plusDays(2));
            meetingSoon.setEndTime(now.plusDays(2).plusHours(1));

            MeetingAttendee pendingLater = new MeetingAttendee();
            pendingLater.setId(2L);
            pendingLater.setMeetingId(2L);
            pendingLater.setEmail(testUser.getEmail());
            pendingLater.setStatus(InvitationStatus.PENDING);
            pendingLater.setInvitedAt(now.minusDays(2));

            Meeting meetingLater = new Meeting();
            meetingLater.setId(2L);
            meetingLater.setTitle("Meeting Next Week");
            meetingLater.setOrganizerEmail("org2@example.com");
            meetingLater.setStartTime(now.plusDays(10)); // After 7 days
            meetingLater.setEndTime(now.plusDays(10).plusHours(1));

            MeetingAttendee accepted = new MeetingAttendee();
            accepted.setId(3L);
            accepted.setMeetingId(3L);
            accepted.setEmail(testUser.getEmail());
            accepted.setStatus(InvitationStatus.ACCEPTED);
            accepted.setInvitedAt(now.minusDays(3));

            Meeting meetingAccepted = new Meeting();
            meetingAccepted.setId(3L);
            meetingAccepted.setTitle("Accepted Meeting");
            meetingAccepted.setOrganizerEmail("org3@example.com");
            meetingAccepted.setStartTime(now.plusDays(3));
            meetingAccepted.setEndTime(now.plusDays(3).plusHours(1));

            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of(pendingSoon, pendingLater, accepted));
            given(meetingRepository.findAllById(Set.of(1L, 2L, 3L)))
                    .willReturn(List.of(meetingSoon, meetingLater, meetingAccepted));

            // Act
            List<InvitationResponse> result = invitationService.getWeeklyInvitations(testUser);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMeetingTitle()).isEqualTo("Meeting Tomorrow");
            assertThat(result.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        }

        @Test
        @DisplayName("Should return empty list when no pending invitations")
        void getWeeklyInvitations_NoPendingInvitations() {
            // Arrange
            MeetingAttendee accepted = new MeetingAttendee();
            accepted.setId(1L);
            accepted.setMeetingId(1L);
            accepted.setEmail(testUser.getEmail());
            accepted.setStatus(InvitationStatus.ACCEPTED);

            Meeting meeting = new Meeting();
            meeting.setId(1L);
            meeting.setTitle("Meeting");
            meeting.setStartTime(LocalDateTime.now().plusDays(1));

            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of(accepted));
            given(meetingRepository.findAllById(Set.of(1L)))
                    .willReturn(List.of(meeting));

            // Act
            List<InvitationResponse> result = invitationService.getWeeklyInvitations(testUser);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when repository returns empty")
        void getWeeklyInvitations_EmptyRepository() {
            // Arrange
            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of());

            // Act
            List<InvitationResponse> result = invitationService.getWeeklyInvitations(testUser);

            // Assert
            assertThat(result).isEmpty();
            then(meetingRepository).should(never()).findAllById(any());
        }

        @Test
        @DisplayName("Should filter out meetings that already started")
        void getWeeklyInvitations_FilterPastMeetings() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();

            MeetingAttendee pendingPast = new MeetingAttendee();
            pendingPast.setId(1L);
            pendingPast.setMeetingId(1L);
            pendingPast.setEmail(testUser.getEmail());
            pendingPast.setStatus(InvitationStatus.PENDING);
            pendingPast.setInvitedAt(now.minusDays(2));

            Meeting meetingPast = new Meeting();
            meetingPast.setId(1L);
            meetingPast.setTitle("Past Meeting");
            meetingPast.setStartTime(now.minusHours(1)); // Already started

            MeetingAttendee pendingFuture = new MeetingAttendee();
            pendingFuture.setId(2L);
            pendingFuture.setMeetingId(2L);
            pendingFuture.setEmail(testUser.getEmail());
            pendingFuture.setStatus(InvitationStatus.PENDING);
            pendingFuture.setInvitedAt(now.minusDays(1));

            Meeting meetingFuture = new Meeting();
            meetingFuture.setId(2L);
            meetingFuture.setTitle("Future Meeting");
            meetingFuture.setStartTime(now.plusHours(1));

            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of(pendingPast, pendingFuture));
            given(meetingRepository.findAllById(Set.of(1L, 2L)))
                    .willReturn(List.of(meetingPast, meetingFuture));

            // Act
            List<InvitationResponse> result = invitationService.getWeeklyInvitations(testUser);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMeetingTitle()).isEqualTo("Future Meeting");
        }
    }

    @Nested
    @DisplayName("Invitation History Tests")
    class InvitationHistoryTests {

        @Test
        @DisplayName("Should return all invitations sorted by invitedAt descending")
        void getInvitationHistory_Success() {
            // Arrange
            LocalDateTime now = LocalDateTime.now();

            MeetingAttendee attendee1 = new MeetingAttendee();
            attendee1.setId(1L);
            attendee1.setMeetingId(1L);
            attendee1.setEmail(testUser.getEmail());
            attendee1.setStatus(InvitationStatus.PENDING);
            attendee1.setInvitedAt(now.minusDays(1));

            Meeting meeting1 = new Meeting();
            meeting1.setId(1L);
            meeting1.setTitle("Meeting 1");
            meeting1.setOrganizerEmail("org1@example.com");
            meeting1.setStartTime(now.plusDays(1));
            meeting1.setEndTime(now.plusDays(1).plusHours(1));

            MeetingAttendee attendee2 = new MeetingAttendee();
            attendee2.setId(2L);
            attendee2.setMeetingId(2L);
            attendee2.setEmail(testUser.getEmail());
            attendee2.setStatus(InvitationStatus.ACCEPTED);
            attendee2.setInvitedAt(now.minusDays(2));
            attendee2.setRespondedAt(now.minusDays(1));

            Meeting meeting2 = new Meeting();
            meeting2.setId(2L);
            meeting2.setTitle("Meeting 2");
            meeting2.setOrganizerEmail("org2@example.com");
            meeting2.setStartTime(now.plusDays(2));
            meeting2.setEndTime(now.plusDays(2).plusHours(1));

            MeetingAttendee attendee3 = new MeetingAttendee();
            attendee3.setId(3L);
            attendee3.setMeetingId(3L);
            attendee3.setEmail(testUser.getEmail());
            attendee3.setStatus(InvitationStatus.DECLINED);
            attendee3.setInvitedAt(now.minusDays(3));
            attendee3.setRespondedAt(now.minusDays(2));
            attendee3.setResponseReason("Not available");

            Meeting meeting3 = new Meeting();
            meeting3.setId(3L);
            meeting3.setTitle("Meeting 3");
            meeting3.setOrganizerEmail("org3@example.com");
            meeting3.setStartTime(now.plusDays(3));
            meeting3.setEndTime(now.plusDays(3).plusHours(1));

            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of(attendee1, attendee2, attendee3));
            given(meetingRepository.findAllById(Set.of(1L, 2L, 3L)))
                    .willReturn(List.of(meeting1, meeting2, meeting3));

            // Act
            List<InvitationResponse> result = invitationService.getInvitationHistory(testUser);

            // Assert
            assertThat(result).hasSize(3);
            
            // Should be sorted by invitedAt descending (newest first)
            assertThat(result.get(0).getMeetingTitle()).isEqualTo("Meeting 1");
            assertThat(result.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
            
            assertThat(result.get(1).getMeetingTitle()).isEqualTo("Meeting 2");
            assertThat(result.get(1).getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            
            assertThat(result.get(2).getMeetingTitle()).isEqualTo("Meeting 3");
            assertThat(result.get(2).getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(result.get(2).getResponseReason()).isEqualTo("Not available");
        }

        @Test
        @DisplayName("Should skip invitations when meeting not found")
        void getInvitationHistory_MeetingNotFound() {
            // Arrange
            MeetingAttendee attendee = new MeetingAttendee();
            attendee.setId(1L);
            attendee.setMeetingId(999L);
            attendee.setEmail(testUser.getEmail());
            attendee.setStatus(InvitationStatus.PENDING);

            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of(attendee));
            given(meetingRepository.findAllById(Set.of(999L)))
                    .willReturn(List.of());

            // Act
            List<InvitationResponse> result = invitationService.getInvitationHistory(testUser);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when no invitations exist")
        void getInvitationHistory_NoInvitations() {
            // Arrange
            given(meetingAttendeeRepository.findAllByEmailOrderByInvitedAtDesc(testUser.getEmail()))
                    .willReturn(List.of());

            // Act
            List<InvitationResponse> result = invitationService.getInvitationHistory(testUser);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Integration Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle multiple accept calls correctly")
        void multipleAcceptCalls() {
            // Arrange
            Long attendeeId = 100L;
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.of(testMeeting));
            given(meetingAttendeeRepository.save(any(MeetingAttendee.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // Act - First accept should succeed
            invitationService.acceptInvitation(attendeeId, testUser);

            // Assert - Second accept should fail
            BadRequestException exception = catchThrowableOfType(
                    () -> invitationService.acceptInvitation(attendeeId, testUser),
                    BadRequestException.class
            );

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Lời mời này đã được phản hồi trước đó");
        }

        @Test
        @DisplayName("Should handle null or blank decline reason")
        void declineInvitation_BlankReason() {
            // Arrange
            Long attendeeId = 100L;
            DeclineInvitationRequest request = new DeclineInvitationRequest();
            request.setReason("   "); // Blank reason
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.of(testMeeting));
            given(meetingAttendeeRepository.save(any(MeetingAttendee.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // Act
            invitationService.declineInvitation(attendeeId, request, testUser);

            // Assert
            assertThat(testAttendee.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(testAttendee.getResponseReason()).isEqualTo(""); // Trimmed to empty
        }

        @Test
        @DisplayName("Should capture saved attendee correctly")
        void acceptInvitation_VerifySavedAttendee() {
            // Arrange
            Long attendeeId = 100L;
            ArgumentCaptor<MeetingAttendee> attendeeCaptor = ArgumentCaptor.forClass(MeetingAttendee.class);
            
            given(meetingAttendeeRepository.findByIdAndEmail(attendeeId, testUser.getEmail()))
                    .willReturn(Optional.of(testAttendee));
            given(meetingRepository.findById(testAttendee.getMeetingId()))
                    .willReturn(Optional.of(testMeeting));
            given(meetingAttendeeRepository.save(any(MeetingAttendee.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // Act
            invitationService.acceptInvitation(attendeeId, testUser);

            // Assert
            then(meetingAttendeeRepository).should().save(attendeeCaptor.capture());
            MeetingAttendee savedAttendee = attendeeCaptor.getValue();
            
            assertThat(savedAttendee.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(savedAttendee.getEmail()).isEqualTo("test@example.com");
            assertThat(savedAttendee.getRespondedAt()).isNotNull();
        }
    }
}
