package com.example.cs.vo;

import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 各专业选课分布条目（统计报表用）。
 */
@Data
public class MajorStatVO {

    private String major;
    private Long studentCount;
    private Long enrollmentCount;
    /** 人均选课门数 */
    private BigDecimal avgCoursesPerStudent;

    public static MajorStatVO of(Object[] r) {
        MajorStatVO v = new MajorStatVO();
        v.major = SqlUtils.toStr(r[0]);
        v.studentCount = SqlUtils.toLong(r[1]);
        v.enrollmentCount = SqlUtils.toLong(r[2]);
        v.avgCoursesPerStudent = SqlUtils.toDecimal(r[3]);
        return v;
    }
}
