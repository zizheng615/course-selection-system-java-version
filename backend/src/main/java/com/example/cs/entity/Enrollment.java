package com.example.cs.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选课记录表 —— 拆解 student 与 course 的多对多关系。
 *
 * <p>唯一索引 uk_student_course(student_id, course_id) 是「防重复选课」的数据库级兜底：
 * 即使应用层的校验被并发绕过，数据库仍会拒绝第二条插入。
 */
@Data
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** SELECTED 已选 / WITHDRAWN 已退 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 成绩，NULL 表示未录入 */
    @Column(name = "score", precision = 5, scale = 1)
    private BigDecimal score;

    @Column(name = "selected_at", insertable = false, updatable = false)
    private LocalDateTime selectedAt;
}
