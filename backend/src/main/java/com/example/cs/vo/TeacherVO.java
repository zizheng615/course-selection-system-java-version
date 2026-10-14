package com.example.cs.vo;

import com.example.cs.entity.Teacher;
import lombok.Data;

@Data
public class TeacherVO {

    private Long id;
    private Long userId;
    private String teacherNo;
    private String name;
    private String title;
    private String dept;

    public static TeacherVO of(Teacher t) {
        TeacherVO v = new TeacherVO();
        v.id = t.getId();
        v.userId = t.getUserId();
        v.teacherNo = t.getTeacherNo();
        v.name = t.getName();
        v.title = t.getTitle();
        v.dept = t.getDept();
        return v;
    }
}
