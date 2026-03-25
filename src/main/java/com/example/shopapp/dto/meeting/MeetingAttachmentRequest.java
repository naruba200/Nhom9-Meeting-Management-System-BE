package com.example.shopapp.dto.meeting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeetingAttachmentRequest {

    @NotBlank(message = "Tên tài liệu không được để trống")
    @Size(max = 255, message = "Tên tài liệu tối đa 255 ký tự")
    private String fileName;

    @Size(max = 100, message = "Loại tài liệu tối đa 100 ký tự")
    private String fileType;

    @Min(value = 0, message = "Kích thước tài liệu phải lớn hơn hoặc bằng 0")
    @Max(value = 52428800, message = "Kích thước tài liệu tối đa 50MB")
    private Long fileSizeBytes;
}
