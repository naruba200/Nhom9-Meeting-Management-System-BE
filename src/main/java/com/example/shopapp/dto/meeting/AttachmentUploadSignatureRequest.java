package com.example.shopapp.dto.meeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttachmentUploadSignatureRequest {

    @NotBlank(message = "Tên file là bắt buộc")
    @Size(max = 255, message = "Tên file tối đa 255 ký tự")
    private String fileName;
}
