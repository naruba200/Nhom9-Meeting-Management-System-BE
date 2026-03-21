package com.example.shopapp.service;

import com.example.shopapp.dto.meeting.AgendaItemRequest;
import com.example.shopapp.dto.meeting.AttachmentUploadSignatureResponse;
import com.example.shopapp.dto.meeting.AttachmentUploadSignatureRequest;
import com.example.shopapp.dto.meeting.ConfirmMeetingAttachmentUploadRequest;
import com.example.shopapp.dto.meeting.CreateMeetingRequest;
import com.example.shopapp.dto.meeting.InviteMeetingRequest;
import com.example.shopapp.dto.meeting.MeetingAgendaItemResponse;
import com.example.shopapp.dto.meeting.MeetingAttachmentResponse;
import com.example.shopapp.dto.meeting.MeetingAttendeeResponse;
import com.example.shopapp.dto.meeting.MeetingResponse;
import com.example.shopapp.dto.meeting.UpdateMeetingAgendaRequest;
import com.example.shopapp.dto.meeting.UpdateMeetingRequest;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAgendaItem;
import com.example.shopapp.entity.MeetingAttachment;
import com.example.shopapp.entity.MeetingAttendee;
import com.example.shopapp.entity.User;
import com.example.shopapp.enums.InvitationStatus;
import com.example.shopapp.enums.MeetingStatus;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.MeetingAgendaItemRepository;
import com.example.shopapp.repository.MeetingAttachmentRepository;
import com.example.shopapp.repository.MeetingAttendeeRepository;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingAgendaItemRepository meetingAgendaItemRepository;
    private final MeetingAttachmentRepository meetingAttachmentRepository;
    private final MeetingAttendeeRepository meetingAttendeeRepository;
    private final UserRepository userRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final GoogleCalendarService googleCalendarService;
    private final NotificationService notificationService;
    private final AsyncNotificationService asyncNotificationService;

    @Transactional
    public MeetingResponse createMeeting(CreateMeetingRequest request, User organizer) {
        validateMeetingTime(request.getStartTime(), request.getEndTime());

        List<String> normalizedAttendeeEmails = request.getAttendeeEmails() == null
                ? List.of()
                : request.getAttendeeEmails().stream()
                .map(email -> email.trim().toLowerCase())
                .toList();

        if (normalizedAttendeeEmails.stream().anyMatch(email -> email.equalsIgnoreCase(organizer.getEmail()))) {
            throw new BadRequestException("Bạn không thể tự mời chính mình vào cuộc họp do bạn tạo");
        }

        // Validate: chỉ cho phép mời người dùng đã đăng ký trong hệ thống
        if (!normalizedAttendeeEmails.isEmpty()) {
            // Batch query: tìm tất cả emails tồn tại trong 1 query thay vì N queries
            List<String> existingEmails = userRepository.findExistingEmails(normalizedAttendeeEmails);
            List<String> invalidEmails = normalizedAttendeeEmails.stream()
                    .filter(email -> !existingEmails.contains(email))
                    .toList();

            if (!invalidEmails.isEmpty()) {
                throw new BadRequestException("Các email sau chưa đăng ký trong hệ thống: " + String.join(", ", invalidEmails));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        boolean syncWithGoogleCalendar = Boolean.TRUE.equals(request.getSyncWithGoogleCalendar());

        String meetingLink = request.getExternalMeetingLink();
        String googleCalendarEventId = null;

        if (syncWithGoogleCalendar) {
            GoogleCalendarService.GoogleCalendarSyncResult syncResult = googleCalendarService
                .createEventWithMeetLink(request, organizer);
            googleCalendarEventId = syncResult.eventId();
            if (syncResult.meetLink() != null && !syncResult.meetLink().isBlank()) {
            meetingLink = syncResult.meetLink();
            }
        }

        Meeting meeting = Meeting.builder()
                .title(request.getTitle().trim())
                .agenda(request.getAgenda())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .organizerEmail(organizer.getEmail())
            .room(syncWithGoogleCalendar ? "Google Meet" : "Online")
            .meetingLink(meetingLink)
            .googleCalendarEventId(googleCalendarEventId)
            .syncedWithGoogleCalendar(syncWithGoogleCalendar)
                .status(MeetingStatus.SCHEDULED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Meeting savedMeeting = meetingRepository.save(meeting);
        replaceAgendaItems(savedMeeting.getId(), request.getAgendaItems());
        // Xử lý mời người dùng trong background để response ngay lập tức
        asyncNotificationService.processInvitationAsync(savedMeeting.getId(), normalizedAttendeeEmails, organizer.getEmail());
        return toResponse(savedMeeting);
    }

    public List<MeetingResponse> getMeetingsForUser(User user) {
        String userEmail = user.getEmail();

        List<Meeting> organizerMeetings = meetingRepository.findAllByOrganizerEmailOrderByStartTimeDesc(userEmail);

        List<Long> acceptedMeetingIds = meetingAttendeeRepository
                .findAllByEmailAndStatus(userEmail, InvitationStatus.ACCEPTED)
                .stream()
                .map(MeetingAttendee::getMeetingId)
                .distinct()
                .toList();

        List<Meeting> attendeeMeetings = acceptedMeetingIds.isEmpty()
                ? List.of()
                : meetingRepository.findAllByIdInOrderByStartTimeDesc(acceptedMeetingIds);

        List<Meeting> mergedMeetings = mergeMeetings(organizerMeetings, attendeeMeetings);
        markExpiredScheduledMeetingsAsCompleted(mergedMeetings);

        return mergedMeetings.stream()
                .sorted(Comparator.comparing(Meeting::getStartTime).reversed())
                .map(this::toResponse)
                .toList();
    }

    private void markExpiredScheduledMeetingsAsCompleted(List<Meeting> meetings) {
        LocalDateTime now = LocalDateTime.now();
        List<Meeting> expiredMeetings = meetings.stream()
                .filter(meeting -> meeting.getStatus() == MeetingStatus.SCHEDULED)
                .filter(meeting -> meeting.getEndTime() != null && meeting.getEndTime().isBefore(now))
                .toList();

        if (expiredMeetings.isEmpty()) {
            return;
        }

        expiredMeetings.forEach(meeting -> {
            meeting.setStatus(MeetingStatus.COMPLETED);
            meeting.setUpdatedAt(now);
        });

        meetingRepository.saveAll(expiredMeetings);
    }

    private List<Meeting> mergeMeetings(List<Meeting> organizerMeetings, List<Meeting> attendeeMeetings) {
        Map<Long, Meeting> mergedById = new LinkedHashMap<>();

        for (Meeting meeting : organizerMeetings) {
            mergedById.put(meeting.getId(), meeting);
        }

        for (Meeting meeting : attendeeMeetings) {
            mergedById.putIfAbsent(meeting.getId(), meeting);
        }

        return new ArrayList<>(mergedById.values());
    }

    @Transactional
    public MeetingResponse updateMeeting(Long meetingId, UpdateMeetingRequest request, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        if (!organizer.getEmail().equalsIgnoreCase(meeting.getOrganizerEmail())) {
            throw new BadRequestException("Bạn không thể chỉnh sửa cuộc họp này");
        }

        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BadRequestException("Không thể chỉnh sửa cuộc họp đã hủy hoặc đã hoàn thành");
        }

        validateMeetingTime(request.getStartTime(), request.getEndTime());

        meeting.setTitle(request.getTitle().trim());
        meeting.setAgenda(request.getAgenda());
        meeting.setStartTime(request.getStartTime());
        meeting.setEndTime(request.getEndTime());

        if (!meeting.isSyncedWithGoogleCalendar()) {
            meeting.setMeetingLink(request.getExternalMeetingLink());
        }

        // Đồng bộ với Google Calendar nếu được yêu cầu và cuộc họp đã đồng bộ trước đó
        boolean shouldSyncWithGoogle = Boolean.TRUE.equals(request.getSyncWithGoogleCalendar());
        if (shouldSyncWithGoogle && meeting.isSyncedWithGoogleCalendar() && meeting.getGoogleCalendarEventId() != null) {
            googleCalendarService.updateEvent(meeting, request, organizer);
        }

        meeting.setUpdatedAt(LocalDateTime.now());

        Meeting savedMeeting = meetingRepository.save(meeting);
        replaceAgendaItems(savedMeeting.getId(), request.getAgendaItems());
        // Xử lý thông báo trong background để response ngay lập tức
        asyncNotificationService.processMeetingUpdatedAsync(savedMeeting.getId(), organizer.getEmail());
        return toResponse(savedMeeting);
    }

        public AttachmentUploadSignatureResponse createAttachmentUploadSignature(
            Long meetingId,
            AttachmentUploadSignatureRequest request,
            User organizer
        ) {
        Meeting meeting = getOrganizerOwnedMeeting(meetingId, organizer);
        ensureMeetingCanBeEdited(meeting);

        CloudinaryUploadService.SignedUploadPayload payload = cloudinaryUploadService
            .createSignedUploadPayload(meetingId, request.getFileName());

        return AttachmentUploadSignatureResponse.builder()
            .cloudName(payload.cloudName())
            .apiKey(payload.apiKey())
            .timestamp(payload.timestamp())
            .signature(payload.signature())
            .folder(payload.folder())
            .publicId(payload.publicId())
            .resourceType(payload.resourceType())
            .build();
        }

        @Transactional
        public MeetingAttachmentResponse confirmMeetingAttachmentUpload(
            Long meetingId,
            ConfirmMeetingAttachmentUploadRequest request,
            User organizer
        ) {
        Meeting meeting = getOrganizerOwnedMeeting(meetingId, organizer);
        ensureMeetingCanBeEdited(meeting);

        cloudinaryUploadService.validateSecureUrl(request.getSecureUrl());

        LocalDateTime now = LocalDateTime.now();
        MeetingAttachment attachment = meetingAttachmentRepository
            .findByMeetingIdAndCloudPublicId(meetingId, request.getCloudPublicId())
            .orElseGet(() -> MeetingAttachment.builder()
                .meetingId(meetingId)
                .createdAt(now)
                .build());

        attachment.setFileName(request.getFileName().trim());
        attachment.setFileType(request.getFileType() == null ? null : request.getFileType().trim());
        attachment.setFileSizeBytes(request.getFileSizeBytes() == null ? 0L : request.getFileSizeBytes());
        attachment.setCloudPublicId(request.getCloudPublicId().trim());
        attachment.setCloudUploadUrl(request.getSecureUrl().trim());
        attachment.setCloudUploadStatus("UPLOADED");
        attachment.setUpdatedAt(now);

        MeetingAttachment savedAttachment = meetingAttachmentRepository.save(attachment);

        meeting.setUpdatedAt(now);
        meetingRepository.save(meeting);

        return toAttachmentResponse(savedAttachment);
        }

    @Transactional
    public void deleteMeetingAttachment(Long meetingId, Long attachmentId, User organizer) {
        Meeting meeting = getOrganizerOwnedMeeting(meetingId, organizer);
        ensureMeetingCanBeEdited(meeting);

        MeetingAttachment attachment = meetingAttachmentRepository
                .findByIdAndMeetingId(attachmentId, meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy tài liệu đính kèm"));

        meetingAttachmentRepository.delete(attachment);

        meeting.setUpdatedAt(LocalDateTime.now());
        meetingRepository.save(meeting);
    }

    @Transactional
    public MeetingResponse updateMeetingAgenda(Long meetingId, UpdateMeetingAgendaRequest request, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        if (!organizer.getEmail().equalsIgnoreCase(meeting.getOrganizerEmail())) {
            throw new BadRequestException("Bạn không thể chỉnh sửa agenda của cuộc họp này");
        }

        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BadRequestException("Không thể chỉnh sửa agenda của cuộc họp đã hủy hoặc đã hoàn thành");
        }

        replaceAgendaItems(meeting.getId(), request.getAgendaItems());
        meeting.setUpdatedAt(LocalDateTime.now());
        Meeting savedMeeting = meetingRepository.save(meeting);
        return toResponse(savedMeeting);
    }

    @Transactional
    public MeetingResponse cancelMeeting(Long meetingId, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        if (!organizer.getEmail().equalsIgnoreCase(meeting.getOrganizerEmail())) {
            throw new BadRequestException("Bạn không thể hủy cuộc họp này");
        }

        if (meeting.getStatus() == MeetingStatus.CANCELLED) {
            throw new BadRequestException("Cuộc họp này đã được hủy trước đó");
        }

        if (meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BadRequestException("Không thể hủy cuộc họp đã hoàn thành");
        }

        if (meeting.isSyncedWithGoogleCalendar()
                && meeting.getGoogleCalendarEventId() != null
                && !meeting.getGoogleCalendarEventId().isBlank()) {
            googleCalendarService.deleteEvent(meeting, organizer);
        }

        LocalDateTime now = LocalDateTime.now();
        meeting.setStatus(MeetingStatus.CANCELLED);
        meeting.setCancelledAt(now);
        meeting.setUpdatedAt(now);

        Meeting savedMeeting = meetingRepository.save(meeting);
        // Xử lý thông báo trong background để response ngay lập tức
        asyncNotificationService.processMeetingCancelledAsync(savedMeeting.getId(), organizer.getEmail());
        return toResponse(savedMeeting);
    }

    @Transactional
    public void inviteAttendees(Long meetingId, InviteMeetingRequest request, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        if (!organizer.getEmail().equalsIgnoreCase(meeting.getOrganizerEmail())) {
            throw new BadRequestException("Bạn không có quyền mời người tham gia cuộc họp này");
        }

        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BadRequestException("Không thể mời người tham gia cuộc họp đã hủy hoặc đã hoàn thành");
        }

        // Normalize emails
        List<String> normalizedEmails = request.getAttendeeEmails().stream()
                .map(email -> email.trim().toLowerCase())
                .toList();

        if (normalizedEmails.stream().anyMatch(email -> email.equalsIgnoreCase(organizer.getEmail()))) {
            throw new BadRequestException("Bạn không thể tự mời chính mình vào cuộc họp do bạn tạo");
        }

        // Validate: chỉ cho phép mời người dùng đã đăng ký trong hệ thống
        // Batch query: tìm tất cả emails tồn tại trong 1 query thay vì N queries
        List<String> existingEmails = userRepository.findExistingEmails(normalizedEmails);
        List<String> invalidEmails = normalizedEmails.stream()
                .filter(email -> !existingEmails.contains(email))
                .toList();

        if (!invalidEmails.isEmpty()) {
            throw new BadRequestException("Các email sau chưa đăng ký trong hệ thống: " + String.join(", ", invalidEmails));
        }

        // Check for already-invited attendees
        // Batch query: tìm tất cả attendees đã được mời trong 1 query thay vì N queries
        List<MeetingAttendee> existingAttendees = meetingAttendeeRepository.findAllByMeetingIdAndEmailIn(meetingId, normalizedEmails);
        List<String> alreadyInvitedEmails = existingAttendees.stream()
                .map(MeetingAttendee::getEmail)
                .toList();

        if (!alreadyInvitedEmails.isEmpty()) {
            throw new BadRequestException("Những email sau đã được mời trong cuộc họp này: " + String.join(", ", alreadyInvitedEmails));
        }

        // Xử lý mời người dùng trong background để response ngay lập tức
        asyncNotificationService.processInvitationAsync(meetingId, normalizedEmails, organizer.getEmail());
    }

    @Transactional
    public MeetingResponse removeAttendee(Long meetingId, String attendeeEmail, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        if (!organizer.getEmail().equalsIgnoreCase(meeting.getOrganizerEmail())) {
            throw new BadRequestException("Bạn không có quyền xóa người tham gia cuộc họp này");
        }

        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BadRequestException("Không thể xóa người tham gia khỏi cuộc họp đã hủy hoặc đã hoàn thành");
        }

        String normalizedEmail = attendeeEmail == null ? "" : attendeeEmail.trim().toLowerCase();
        if (normalizedEmail.isBlank()) {
            throw new BadRequestException("Email người tham gia là bắt buộc");
        }

        if (normalizedEmail.equalsIgnoreCase(organizer.getEmail())) {
            throw new BadRequestException("Bạn không thể xóa chính mình khỏi cuộc họp do bạn tạo");
        }

        MeetingAttendee attendee = meetingAttendeeRepository.findByMeetingIdAndEmail(meetingId, normalizedEmail)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người tham gia trong cuộc họp này"));

        meetingAttendeeRepository.delete(attendee);
        meeting.setUpdatedAt(LocalDateTime.now());
        Meeting savedMeeting = meetingRepository.save(meeting);

        return toResponse(savedMeeting);
    }

    private void validateMeetingTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }

    private MeetingResponse toResponse(Meeting meeting) {
        List<MeetingAttendeeResponse> attendees = meetingAttendeeRepository.findAllByMeetingId(meeting.getId())
            .stream()
            .map(this::toAttendeeResponse)
            .toList();
        List<MeetingAgendaItemResponse> agendaItems = meetingAgendaItemRepository
            .findAllByMeetingIdOrderByItemOrderAsc(meeting.getId())
            .stream()
            .map(this::toAgendaItemResponse)
            .toList();
        List<MeetingAttachmentResponse> attachments = meetingAttachmentRepository
            .findAllByMeetingIdOrderByIdAsc(meeting.getId())
            .stream()
            .map(this::toAttachmentResponse)
            .toList();
        int totalAgendaDurationMinutes = agendaItems.stream()
            .mapToInt(item -> item.getDurationMinutes() == null ? 0 : item.getDurationMinutes())
            .sum();

        return MeetingResponse.builder()
                .id(meeting.getId())
                .title(meeting.getTitle())
                .agenda(meeting.getAgenda())
                .startTime(meeting.getStartTime())
                .endTime(meeting.getEndTime())
                .organizerEmail(meeting.getOrganizerEmail())
            .meetingLink(meeting.getMeetingLink())
            .googleCalendarEventId(meeting.getGoogleCalendarEventId())
            .syncedWithGoogleCalendar(meeting.isSyncedWithGoogleCalendar())
                .status(meeting.getStatus())
                .createdAt(meeting.getCreatedAt())
                .updatedAt(meeting.getUpdatedAt())
                .attendees(attendees)
                .agendaItems(agendaItems)
                .attachments(attachments)
                .totalAgendaDurationMinutes(totalAgendaDurationMinutes)
                .build();
    }

    private void replaceAgendaItems(Long meetingId, List<AgendaItemRequest> requests) {
        meetingAgendaItemRepository.deleteAllByMeetingId(meetingId);
        if (requests == null || requests.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        List<AgendaItemRequest> sortedRequests = requests.stream()
                .sorted(Comparator.comparing(item -> item.getItemOrder() == null ? Integer.MAX_VALUE : item.getItemOrder()))
                .toList();

        List<MeetingAgendaItem> agendaItems = new ArrayList<>();
        for (int i = 0; i < sortedRequests.size(); i++) {
            AgendaItemRequest request = sortedRequests.get(i);
            agendaItems.add(MeetingAgendaItem.builder()
                    .meetingId(meetingId)
                    .title(request.getTitle().trim())
                    .presenter("")
                    .durationMinutes(request.getDurationMinutes())
                    .description(request.getDescription() == null ? null : request.getDescription().trim())
                    .itemOrder(i + 1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        meetingAgendaItemRepository.saveAll(agendaItems);
    }

    private Meeting getOrganizerOwnedMeeting(Long meetingId, User organizer) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy cuộc họp"));

        if (!organizer.getEmail().equalsIgnoreCase(meeting.getOrganizerEmail())) {
            throw new BadRequestException("Bạn không có quyền thao tác tài liệu của cuộc họp này");
        }

        return meeting;
    }

    private void ensureMeetingCanBeEdited(Meeting meeting) {
        if (meeting.getStatus() == MeetingStatus.CANCELLED || meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new BadRequestException("Không thể thao tác tài liệu của cuộc họp đã hủy hoặc đã hoàn thành");
        }
    }

    private MeetingAttendeeResponse toAttendeeResponse(MeetingAttendee attendee) {
        return MeetingAttendeeResponse.builder()
                .id(attendee.getId())
                .email(attendee.getEmail())
                .status(attendee.getStatus())
                .responseReason(attendee.getResponseReason())
                .invitedAt(attendee.getInvitedAt())
                .respondedAt(attendee.getRespondedAt())
                .build();
    }

    private MeetingAgendaItemResponse toAgendaItemResponse(MeetingAgendaItem item) {
        return MeetingAgendaItemResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .durationMinutes(item.getDurationMinutes())
                .description(item.getDescription())
                .itemOrder(item.getItemOrder())
                .build();
    }

    private MeetingAttachmentResponse toAttachmentResponse(MeetingAttachment attachment) {
        return MeetingAttachmentResponse.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .fileSizeBytes(attachment.getFileSizeBytes())
                .cloudUploadUrl(attachment.getCloudUploadUrl())
                .cloudPublicId(attachment.getCloudPublicId())
                .cloudUploadStatus(attachment.getCloudUploadStatus())
                .build();
    }
}
