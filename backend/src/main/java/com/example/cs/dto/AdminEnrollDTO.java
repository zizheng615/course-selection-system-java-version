package com.example.cs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员代学生加选课的入参。
 */
@Data
public class AdminEnrollDTO {

    @NotNull(message = "学生不能为空")
    private Long studentId;

    @NotNull(message = "课程不能为空")
    private Long courseId;
}
