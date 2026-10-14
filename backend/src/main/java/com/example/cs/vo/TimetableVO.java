package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课表条目：一门课的一段上课时间。
 * 对应 EnrollmentRepository#findTimetable。
 */
@Data
public class TimetableVO {

    private Long courseId;
    private String courseCode;
    private String courseName;
    private BigDecimal credit;
    private String courseType;
    private String teacherName;
    private Integer weekday;
    private String weekdayName;
    private Integer startSection;
    private Integer endSection;
    private Integer startWeek;
    private Integer endWeek;
    private String classroom;

    public static TimetableVO of(Object[] r) {
        TimetableVO v = new TimetableVO();
        v.courseId = SqlUtils.toLong(r[0]);
        v.courseCode = SqlUtils.toStr(r[1]);
        v.courseName = SqlUtils.toStr(r[2]);
        v.credit = SqlUtils.toDecimal(r[3]);
        v.courseType = SqlUtils.toStr(r[4]);
        v.teacherName = SqlUtils.toStr(r[5]);
        v.weekday = SqlUtils.toInt(r[6]);
        v.weekdayName = ScheduleVO.weekdayName(v.weekday);
        v.startSection = SqlUtils.toInt(r[7]);
        v.endSection = SqlUtils.toInt(r[8]);
        v.startWeek = SqlUtils.toInt(r[9]);
        v.endWeek = SqlUtils.toInt(r[10]);
        v.classroom = SqlUtils.toStr(r[11]);
        return v;
    }
}
