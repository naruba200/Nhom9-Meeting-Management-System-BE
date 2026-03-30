package com.example.shopapp.dto.meeting;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeetingAttachmentResponse {

    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private String cloudUploadUrl;
    private String cloudPublicId;
    private String cloudUploadStatus;
}
