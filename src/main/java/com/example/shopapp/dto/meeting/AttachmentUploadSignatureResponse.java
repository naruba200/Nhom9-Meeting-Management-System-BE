package com.example.shopapp.dto.meeting;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AttachmentUploadSignatureResponse {

    private String cloudName;
    private String apiKey;
    private Long timestamp;
    private String signature;
    private String folder;
    private String publicId;
    private String resourceType;
}
