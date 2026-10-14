package com.example.cs.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一用户表。
 *
 * <p>认证与业务解耦：学生、教师、管理员共用一张账号表，
 * 各自的业务属性（学号、职称、专业等）放在 student / teacher 表中，
 * 通过 refId 关联。
 */
@Data
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** STUDENT / TEACHER / ADMIN */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "real_name", nullable = false, length = 50)
    private String realName;

    /** 1 启用 / 0 禁用 */
    @Column(name = "status", nullable = false)
    private Integer status;

    /** 由数据库默认值生成，应用侧只读 */
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
