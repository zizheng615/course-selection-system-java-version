package com.example.cs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 排课（上课时间）新增 / 修改入参。
 */
@Data
public class ScheduleDTO {

    @NotNull(message = "课程不能为空")
    private Long courseId;

    @NotNull(message = "星期不能为空")
    @Min(value = 1, message = "星期取值为 1-7")
    @Max(value = 7, message = "星期取值为 1-7")
    private Integer weekday;

    @NotNull(message = "开始节次不能为空")
    @Min(value = 1, message = "节次取值为 1-12")
    @Max(value = 12, message = "节次取值为 1-12")
    private Integer startSection;

    @NotNull(message = "结束节次不能为空")
    @Min(value = 1, message = "节次取值为 1-12")
    @Max(value = 12, message = "节次取值为 1-12")
    private Integer endSection;

    @NotNull(message = "起始周不能为空")
    @Min(value = 1, message = "周次取值为 1-30")
    @Max(value = 30, message = "周次取值为 1-30")
    private Integer startWeek;

    @NotNull(message = "结束周不能为空")
    @Min(value = 1, message = "周次取值为 1-30")
    @Max(value = 30, message = "周次取值为 1-30")
    private Integer endWeek;

    @Size(max = 50, message = "教室长度不能超过 50")
    private String classroom;
}
