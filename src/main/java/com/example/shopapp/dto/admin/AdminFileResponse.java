package com.example.shopapp.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminFileResponse {
    private Long id;
    private Long meetingId;
    private String meetingTitle;
    private String fileName;
    private String fileType;
    private Long fileSizeBytes;
    private String cloudUploadUrl;
    private String cloudPublicId;
    private String cloudUploadStatus;
    private String uploadedBy;
    private String uploadedByEmail;
}
