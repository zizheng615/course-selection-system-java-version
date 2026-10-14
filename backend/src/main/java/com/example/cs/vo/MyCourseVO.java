package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生已选课程（含成绩）。
 * 对应 EnrollmentRepository#findMyCourses。
 */
@Data
public class MyCourseVO {

    private Long enrollmentId;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private BigDecimal credit;
    private String courseType;
    private String semester;
    private String teacherName;
    private String status;
    /** null 表示未录入成绩 */
    private BigDecimal score;
    private LocalDateTime selectedAt;

    public static MyCourseVO of(Object[] r) {
        MyCourseVO v = new MyCourseVO();
        v.enrollmentId = SqlUtils.toLong(r[0]);
        v.courseId = SqlUtils.toLong(r[1]);
        v.courseCode = SqlUtils.toStr(r[2]);
        v.courseName = SqlUtils.toStr(r[3]);
        v.credit = SqlUtils.toDecimal(r[4]);
        v.courseType = SqlUtils.toStr(r[5]);
        v.semester = SqlUtils.toStr(r[6]);
        v.teacherName = SqlUtils.toStr(r[7]);
        v.status = SqlUtils.toStr(r[8]);
        v.score = SqlUtils.toDecimal(r[9]);
        v.selectedAt = SqlUtils.toDateTime(r[10]);
        return v;
    }
}
