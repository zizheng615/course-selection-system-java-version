package com.example.cs.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 教师表。
 */
@Data
@Entity
@Table(name = "teacher")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "teacher_no", nullable = false, length = 20, unique = true)
    private String teacherNo;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 职称：教授 / 副教授 / 讲师 */
    @Column(name = "title", length = 20)
    private String title;

    @Column(name = "dept", nullable = false, length = 50)
    private String dept;
}
