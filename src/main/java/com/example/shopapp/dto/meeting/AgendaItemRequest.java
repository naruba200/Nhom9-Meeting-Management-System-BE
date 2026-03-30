package com.example.shopapp.dto.meeting;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AgendaItemRequest {

    @NotBlank(message = "Tiêu đề mục không được để trống")
    @Size(max = 255, message = "Tiêu đề mục tối đa 255 ký tự")
    private String title;

    @Min(value = 1, message = "Thời lượng phải lớn hơn 0 phút")
    private Integer durationMinutes;

    @NotBlank(message = "Mô tả chi tiết không được để trống")
    @Size(max = 2000, message = "Mô tả chi tiết tối đa 2000 ký tự")
    private String description;

    @Min(value = 1, message = "Thứ tự phải bắt đầu từ 1")
    private Integer itemOrder;
}
