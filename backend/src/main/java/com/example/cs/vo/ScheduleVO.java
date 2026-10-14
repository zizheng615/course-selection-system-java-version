package com.example.cs.vo;

import com.example.cs.entity.CourseSchedule;
import lombok.Data;

@Data
public class ScheduleVO {

    private Long id;
    private Long courseId;
    private String courseName;
    private Integer weekday;
    private String weekdayName;
    private Integer startSection;
    private Integer endSection;
    private Integer startWeek;
    private Integer endWeek;
    private String classroom;

    public static ScheduleVO of(CourseSchedule s) {
        ScheduleVO v = new ScheduleVO();
        v.id = s.getId();
        v.courseId = s.getCourseId();
        v.weekday = s.getWeekday();
        v.weekdayName = weekdayName(s.getWeekday());
        v.startSection = s.getStartSection();
        v.endSection = s.getEndSection();
        v.startWeek = s.getStartWeek();
        v.endWeek = s.getEndWeek();
        v.classroom = s.getClassroom();
        return v;
    }

    public static String weekdayName(Integer weekday) {
        if (weekday == null) {
            return "";
        }
        return switch (weekday) {
            case 1 -> "周一";
            case 2 -> "周二";
            case 3 -> "周三";
            case 4 -> "周四";
            case 5 -> "周五";
            case 6 -> "周六";
            case 7 -> "周日";
            default -> "";
        };
    }
}
