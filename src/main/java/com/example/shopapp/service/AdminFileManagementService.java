package com.example.shopapp.service;

import com.example.shopapp.dto.admin.AdminFileResponse;
import com.example.shopapp.dto.admin.AdminFilesPageResponse;
import com.example.shopapp.entity.Meeting;
import com.example.shopapp.entity.MeetingAttachment;
import com.example.shopapp.entity.User;
import com.example.shopapp.exception.BadRequestException;
import com.example.shopapp.repository.MeetingAttachmentRepository;
import com.example.shopapp.repository.MeetingRepository;
import com.example.shopapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminFileManagementService {

    private final MeetingAttachmentRepository attachmentRepository;
    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    public AdminFilesPageResponse getAllFiles(Integer page, Integer size, String fileName, String fileType) {
        int pageNum = page != null && page > 0 ? page - 1 : 0;
        int pageSize = size != null && size > 0 ? size : 10;

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "id"));

        Page<MeetingAttachment> attachmentPage = attachmentRepository.findAll(
                fileName, fileType, pageable);

        List<AdminFileResponse> fileResponses = attachmentPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return AdminFilesPageResponse.builder()
                .files(fileResponses)
                .page(pageNum + 1)
                .size(pageSize)
                .totalElements(attachmentPage.getTotalElements())
                .totalPages(attachmentPage.getTotalPages())
                .isFirst(attachmentPage.isFirst())
                .isLast(attachmentPage.isLast())
                .build();
    }

    public AdminFileResponse getFileById(Long id) {
        MeetingAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy file với ID: " + id));
        return mapToResponse(attachment);
    }

    public void deleteFile(Long id) {
        if (!attachmentRepository.existsById(id)) {
            throw new BadRequestException("Không tìm thấy file với ID: " + id);
        }
        attachmentRepository.deleteById(id);
    }

    public void deleteFiles(List<Long> ids) {
        for (Long id : ids) {
            if (!attachmentRepository.existsById(id)) {
                throw new BadRequestException("Không tìm thấy file với ID: " + id);
            }
        }
        attachmentRepository.deleteAllById(ids);
    }

    private AdminFileResponse mapToResponse(MeetingAttachment attachment) {
        AdminFileResponse.AdminFileResponseBuilder builder = AdminFileResponse.builder()
                .id(attachment.getId())
                .meetingId(attachment.getMeetingId())
                .fileName(attachment.getFileName())
                .fileType(attachment.getFileType())
                .fileSizeBytes(attachment.getFileSizeBytes())
                .cloudUploadUrl(attachment.getCloudUploadUrl())
                .cloudPublicId(attachment.getCloudPublicId())
                .cloudUploadStatus(attachment.getCloudUploadStatus());

        // Fetch meeting title if available
        try {
            Meeting meeting = meetingRepository.findById(attachment.getMeetingId()).orElse(null);
            if (meeting != null) {
                builder.meetingTitle(meeting.getTitle());
                builder.uploadedByEmail(meeting.getOrganizerEmail());
                // Organizer name is not available in Meeting entity, use email as fallback
                builder.uploadedBy(meeting.getOrganizerEmail());
            }
        } catch (Exception e) {
            // Ignore if meeting not found
        }

        return builder.build();
    }
}
