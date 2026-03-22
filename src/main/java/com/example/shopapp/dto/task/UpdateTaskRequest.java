package com.example.shopapp.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskRequest {
    
    @NotBlank(message = "Tiêu đề công việc không được để trống")
    @Size(max = 255, message = "Tiêu đề công việc tối đa 255 ký tự")
    private String title;

    @Size(max = 2000, message = "Mô tả công việc tối đa 2000 ký tự")
    private String description;

    @NotBlank(message = "Email người được giao công việc không được để trống")
    private String assigneeEmail;

    private List<String> subtaskTitles;
}
