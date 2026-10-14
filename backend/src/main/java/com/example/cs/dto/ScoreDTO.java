package com.example.cs.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩录入入参。
 */
@Data
public class ScoreDTO {

    @NotNull(message = "选课记录 ID 不能为空")
    private Long enrollmentId;

    @NotNull(message = "成绩不能为空")
    @DecimalMin(value = "0", message = "成绩不能小于 0")
    @DecimalMax(value = "100", message = "成绩不能大于 100")
    private BigDecimal score;
}
