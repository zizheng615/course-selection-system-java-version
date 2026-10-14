package com.example.cs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SelectCourseDTO {

    @NotNull(message = "课程 ID 不能为空")
    private Long courseId;
}
