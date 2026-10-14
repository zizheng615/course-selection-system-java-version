package com.example.cs.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 课程上课时间。
 *
 * <p>独立成表的原因：
 * <ol>
 *   <li>一门课可能有多段上课时间（如理论课 + 实验课），无法用课程表的单列表达</li>
 *   <li>时间冲突检测需要按 (weekday, 节次区间) 做区间重叠比较，独立成行才能建索引加速</li>
 * </ol>
 */
@Data
@Entity
@Table(name = "course_schedule")
public class CourseSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 1=周一 ... 7=周日 */
    @Column(name = "weekday", nullable = false)
    private Integer weekday;

    @Column(name = "start_section", nullable = false)
    private Integer startSection;

    @Column(name = "end_section", nullable = false)
    private Integer endSection;

    @Column(name = "start_week", nullable = false)
    private Integer startWeek;

    @Column(name = "end_week", nullable = false)
    private Integer endWeek;

    @Column(name = "classroom", length = 50)
    private String classroom;
}
