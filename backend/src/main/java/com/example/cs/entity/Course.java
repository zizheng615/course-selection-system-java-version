package com.example.cs.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程表。
 *
 * <p><b>关于 enrolled_count：</b>这是一个「有意为之的反范式字段」。
 * 若每次都通过 {@code COUNT(*) FROM enrollment} 计算余量，选课页的每次列表查询
 * 都需要对选课记录表做聚合；把计数冗余在课程表上，余量查询退化为一次简单读取。
 * 代价是需要维护一致性 —— 由数据库的 chk_capacity 约束与 Service 层事务共同保证。
 */
@Data
@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false, length = 20)
    private String courseCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** DECIMAL(3,1)，用 BigDecimal 避免浮点误差 */
    @Column(name = "credit", nullable = false, precision = 3, scale = 1)
    private BigDecimal credit;

    @Column(name = "hours", nullable = false)
    private Integer hours;

    /** 必修 / 选修 / 公选 */
    @Column(name = "course_type", nullable = false, length = 20)
    private String courseType;

    @Column(name = "dept", nullable = false, length = 50)
    private String dept;

    @Column(name = "semester", nullable = false, length = 20)
    private String semester;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    /** 已选人数（冗余字段，由选课/退课事务维护） */
    @Column(name = "enrolled_count", nullable = false)
    private Integer enrolledCount;

    /** OPEN 开放选课 / CLOSED 已关闭 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "intro", length = 500)
    private String intro;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 剩余容量（派生属性，不映射到数据库列） */
    @Transient
    public int getRemaining() {
        if (capacity == null || enrolledCount == null) {
            return 0;
        }
        return capacity - enrolledCount;
    }
}
