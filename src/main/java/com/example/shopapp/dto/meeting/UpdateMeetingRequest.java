package com.example.shopapp.dto.meeting;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class UpdateMeetingRequest {

    @NotBlank(message = "Tiêu đề cuộc họp không được để trống")
    @Size(max = 255, message = "Tiêu đề cuộc họp tối đa 255 ký tự")
    private String title;

    @Size(max = 2000, message = "Nội dung chương trình họp tối đa 2000 ký tự")
    private String agenda;

    @NotNull(message = "Thời gian bắt đầu là bắt buộc")
    @Future(message = "Thời gian bắt đầu phải ở tương lai")
    private LocalDateTime startTime;

    @NotNull(message = "Thời gian kết thúc là bắt buộc")
    @Future(message = "Thời gian kết thúc phải ở tương lai")
    private LocalDateTime endTime;

    @Size(max = 500, message = "Link họp tối đa 500 ký tự")
    private String externalMeetingLink;

    private Boolean syncWithGoogleCalendar;

    @Size(max = 100, message = "Múi giờ tối đa 100 ký tự")
    private String timezone;

    @Valid
    private List<AgendaItemRequest> agendaItems;

}
