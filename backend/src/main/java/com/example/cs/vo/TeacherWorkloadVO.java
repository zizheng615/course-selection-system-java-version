package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师工作量统计条目。
 */
@Data
public class TeacherWorkloadVO {

    private String teacherName;
    private String title;
    private String dept;
    private Long courseCount;
    private Long totalStudents;
    private BigDecimal totalCredit;

    public static TeacherWorkloadVO of(Object[] r) {
        TeacherWorkloadVO v = new TeacherWorkloadVO();
        v.teacherName = SqlUtils.toStr(r[0]);
        v.title = SqlUtils.toStr(r[1]);
        v.dept = SqlUtils.toStr(r[2]);
        v.courseCount = SqlUtils.toLong(r[3]);
        v.totalStudents = SqlUtils.toLong(r[4]);
        v.totalCredit = SqlUtils.toDecimal(r[5]);
        return v;
    }
}
