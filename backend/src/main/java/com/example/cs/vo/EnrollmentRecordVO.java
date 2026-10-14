package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选课记录视图对象（管理员选课记录管理页用）。
 * 对应 EnrollmentRepository#searchEnrollments。
 */
@Data
public class EnrollmentRecordVO {

    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String major;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String teacherName;
    private String semester;
    /** SELECTED / WITHDRAWN */
    private String status;
    private BigDecimal score;
    private LocalDateTime selectedAt;

    public static EnrollmentRecordVO of(Object[] r) {
        EnrollmentRecordVO v = new EnrollmentRecordVO();
        v.id = SqlUtils.toLong(r[0]);
        v.studentId = SqlUtils.toLong(r[1]);
        v.studentNo = SqlUtils.toStr(r[2]);
        v.studentName = SqlUtils.toStr(r[3]);
        v.major = SqlUtils.toStr(r[4]);
        v.courseId = SqlUtils.toLong(r[5]);
        v.courseCode = SqlUtils.toStr(r[6]);
        v.courseName = SqlUtils.toStr(r[7]);
        v.teacherName = SqlUtils.toStr(r[8]);
        v.semester = SqlUtils.toStr(r[9]);
        v.status = SqlUtils.toStr(r[10]);
        v.score = SqlUtils.toDecimal(r[11]);
        v.selectedAt = SqlUtils.toDateTime(r[12]);
        return v;
    }
}
