package com.example.shopapp.dto.meeting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmMeetingAttachmentUploadRequest {

    @NotBlank(message = "Tên file là bắt buộc")
    @Size(max = 255, message = "Tên file tối đa 255 ký tự")
    private String fileName;

    @Size(max = 100, message = "Loại file tối đa 100 ký tự")
    private String fileType;

    @Min(value = 0, message = "Kích thước file phải lớn hơn hoặc bằng 0")
    @Max(value = 52428800, message = "Kích thước file tối đa 50MB")
    private Long fileSizeBytes;

    @NotBlank(message = "Cloudinary public id là bắt buộc")
    @Size(max = 255, message = "Cloudinary public id tối đa 255 ký tự")
    private String cloudPublicId;

    @NotBlank(message = "Secure URL là bắt buộc")
    @Size(max = 500, message = "Secure URL tối đa 500 ký tự")
    private String secureUrl;
}
