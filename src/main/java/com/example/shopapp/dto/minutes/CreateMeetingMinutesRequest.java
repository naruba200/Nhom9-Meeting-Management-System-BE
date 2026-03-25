package com.example.shopapp.dto.minutes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMeetingMinutesRequest {

    @NotBlank(message = "Tiêu đề biên bản không được để trống")
    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    private String title;

    @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
    private String location;

    @Size(max = 2000, message = "Mục đích tối đa 2000 ký tự")
    private String purpose;

    @Size(max = 2000, message = "Danh sách tham dự tối đa 2000 ký tự")
    private String attendees;

    @Size(max = 2000, message = "Danh sách vắng mặt tối đa 2000 ký tự")
    private String absentees;

    @Size(max = 10000, message = "Nội dung biên bản tối đa 10000 ký tự")
    private String content;

    @Size(max = 5000, message = "Quyết định tối đa 5000 ký tự")
    private String decisions;

    @Size(max = 5000, message = "Ý kiến tối đa 5000 ký tự")
    private String contributions;

    @Size(max = 2000, message = "Biểu quyết tối đa 2000 ký tự")
    private String voting;

    @Size(max = 5000, message = "Kết thúc tối đa 5000 ký tự")
    private String conclusions;

    @NotNull(message = "Thời gian tạo là bắt buộc")
    private LocalDateTime minutesCreatedAt;

    @NotNull(message = "Thời gian đóng là bắt buộc")
    private LocalDateTime minutesClosedAt;

    private List<Long> taskIds;
}
