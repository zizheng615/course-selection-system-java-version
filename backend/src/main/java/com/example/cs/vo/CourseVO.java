package com.example.cs.vo;

import com.example.cs.entity.Course;
import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程视图对象。
 *
 * <p>两个工厂方法分别对应两条原生查询的列顺序，
 * 用命名区分而非重载，避免传错行数组导致字段错位。
 */
@Data
public class CourseVO {

    private Long id;
    private String courseCode;
    private String name;
    private BigDecimal credit;
    private Integer hours;
    private String courseType;
    private String dept;
    private String semester;
    private Long teacherId;
    private String teacherName;
    private String teacherTitle;
    private Integer capacity;
    private Integer enrolledCount;
    private Integer remaining;
    private String status;
    private String intro;

    /**
     * 仅在「冲突课程查询」中赋值：记录本课程具体与哪门已选课程冲突，
     * 用于前端提示"XX 与 YY 时间冲突"。
     */
    private String conflictWith;

    /** 对应 CourseRepository#searchCourses（16 列） */
    public static CourseVO ofListRow(Object[] r) {
        CourseVO v = new CourseVO();
        v.id = SqlUtils.toLong(r[0]);
        v.courseCode = SqlUtils.toStr(r[1]);
        v.name = SqlUtils.toStr(r[2]);
        v.credit = SqlUtils.toDecimal(r[3]);
        v.hours = SqlUtils.toInt(r[4]);
        v.courseType = SqlUtils.toStr(r[5]);
        v.dept = SqlUtils.toStr(r[6]);
        v.semester = SqlUtils.toStr(r[7]);
        v.teacherId = SqlUtils.toLong(r[8]);
        v.teacherName = SqlUtils.toStr(r[9]);
        v.teacherTitle = SqlUtils.toStr(r[10]);
        v.capacity = SqlUtils.toInt(r[11]);
        v.enrolledCount = SqlUtils.toInt(r[12]);
        v.remaining = SqlUtils.toInt(r[13]);
        v.status = SqlUtils.toStr(r[14]);
        v.intro = SqlUtils.toStr(r[15]);
        return v;
    }

    /** 对应 CourseRepository#findSelectableCourses（11 列，无学期/教师ID/状态/简介） */
    public static CourseVO ofSelectableRow(Object[] r) {
        CourseVO v = new CourseVO();
        v.id = SqlUtils.toLong(r[0]);
        v.courseCode = SqlUtils.toStr(r[1]);
        v.name = SqlUtils.toStr(r[2]);
        v.credit = SqlUtils.toDecimal(r[3]);
        v.courseType = SqlUtils.toStr(r[4]);
        v.dept = SqlUtils.toStr(r[5]);
        v.teacherName = SqlUtils.toStr(r[6]);
        v.teacherTitle = SqlUtils.toStr(r[7]);
        v.capacity = SqlUtils.toInt(r[8]);
        v.enrolledCount = SqlUtils.toInt(r[9]);
        v.remaining = SqlUtils.toInt(r[10]);
        v.status = "OPEN";
        return v;
    }

    public static CourseVO of(Course c) {
        CourseVO v = new CourseVO();
        v.id = c.getId();
        v.courseCode = c.getCourseCode();
        v.name = c.getName();
        v.credit = c.getCredit();
        v.hours = c.getHours();
        v.courseType = c.getCourseType();
        v.dept = c.getDept();
        v.semester = c.getSemester();
        v.teacherId = c.getTeacherId();
        v.capacity = c.getCapacity();
        v.enrolledCount = c.getEnrolledCount();
        v.remaining = c.getRemaining();
        v.status = c.getStatus();
        v.intro = c.getIntro();
        return v;
    }
}
