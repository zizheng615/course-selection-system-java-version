package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程学生名单条目（教师端 / 管理员端）。
 * 对应 EnrollmentRepository#findCourseStudents。
 */
@Data
public class CourseStudentVO {

    private Long enrollmentId;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String major;
    private String grade;
    /** null 表示未录入成绩（SQL 中 -1 已还原为 null） */
    private BigDecimal score;

    public static CourseStudentVO of(Object[] r) {
        CourseStudentVO v = new CourseStudentVO();
        v.enrollmentId = SqlUtils.toLong(r[0]);
        v.studentId = SqlUtils.toLong(r[1]);
        v.studentNo = SqlUtils.toStr(r[2]);
        v.studentName = SqlUtils.toStr(r[3]);
        v.major = SqlUtils.toStr(r[4]);
        v.grade = SqlUtils.toStr(r[5]);
        BigDecimal score = SqlUtils.toDecimal(r[6]);
        // SQL 用 IFNULL(..., -1) 规避 NULL，此处还原为 null 便于前端判断"未录入"
        v.score = (score == null || score.compareTo(BigDecimal.ZERO) < 0) ? null : score;
        return v;
    }
}
