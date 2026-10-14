package com.example.cs.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 学生表。
 *
 * <p>user_id 以普通外键列映射，而非 JPA 的 {@code @ManyToOne}：
 * 关联查询统一走原生 SQL，避免 open-in-view=false 下的懒加载异常，
 * 也让多表统计查询的语义更直白。
 */
@Data
@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "student_no", nullable = false, length = 20, unique = true)
    private String studentNo;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * M 男 / F 女
     *
     * <p>指定 columnDefinition：DDL 中该列是定长的 CHAR(1)（性别是固定单字符编码，
     * CHAR 无长度前缀、更贴合语义），而 Hibernate 对 String 的默认映射是 VARCHAR。
     * 若不显式声明，ddl-auto=validate 会因「期望 varchar(1)、实际 char」而拒绝启动。
     */
    @Column(name = "gender", nullable = false, length = 1, columnDefinition = "char(1)")
    private String gender;

    @Column(name = "major", nullable = false, length = 50)
    private String major;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "phone", length = 20)
    private String phone;
}
