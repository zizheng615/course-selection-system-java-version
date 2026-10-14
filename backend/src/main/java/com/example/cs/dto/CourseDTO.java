package com.example.cs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程新增 / 修改入参。
 */
@Data
public class CourseDTO {

    @NotBlank(message = "课程代码不能为空")
    @Size(max = 20, message = "课程代码长度不能超过 20")
    private String courseCode;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称长度不能超过 100")
    private String name;

    @NotNull(message = "学分不能为空")
    @DecimalMin(value = "0.5", message = "学分不能小于 0.5")
    @DecimalMax(value = "20", message = "学分不能大于 20")
    private BigDecimal credit;

    @NotNull(message = "学时不能为空")
    @Min(value = 1, message = "学时必须大于 0")
    private Integer hours;

    @NotBlank(message = "课程类型不能为空")
    private String courseType;

    @NotBlank(message = "开课院系不能为空")
    private String dept;

    @NotBlank(message = "学期不能为空")
    private String semester;

    @NotNull(message = "授课教师不能为空")
    private Long teacherId;

    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量必须大于 0")
    private Integer capacity;

    /** OPEN / CLOSED，不传默认 OPEN */
    private String status;

    @Size(max = 500, message = "课程简介长度不能超过 500")
    private String intro;
}
