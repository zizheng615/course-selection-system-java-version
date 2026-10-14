package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 热门课程排行条目（统计报表用）。
 */
@Data
public class PopularCourseVO {

    private String courseCode;
    private String courseName;
    private String teacherName;
    private Integer capacity;
    private Long selectedCount;
    /** 满座率（百分比），如 83.3 */
    private BigDecimal fillRate;

    public static PopularCourseVO of(Object[] r) {
        PopularCourseVO v = new PopularCourseVO();
        v.courseCode = SqlUtils.toStr(r[0]);
        v.courseName = SqlUtils.toStr(r[1]);
        v.teacherName = SqlUtils.toStr(r[2]);
        v.capacity = SqlUtils.toInt(r[3]);
        v.selectedCount = SqlUtils.toLong(r[4]);
        v.fillRate = SqlUtils.toDecimal(r[5]);
        return v;
    }
}
