package com.example.cs.vo;

import com.example.cs.entity.Student;
import lombok.Data;

@Data
public class StudentVO {

    private Long id;
    private Long userId;
    private String studentNo;
    private String name;
    private String gender;
    private String major;
    private String grade;
    private String phone;

    public static StudentVO of(Student s) {
        StudentVO v = new StudentVO();
        v.id = s.getId();
        v.userId = s.getUserId();
        v.studentNo = s.getStudentNo();
        v.name = s.getName();
        v.gender = s.getGender();
        v.major = s.getMajor();
        v.grade = s.getGrade();
        v.phone = s.getPhone();
        return v;
    }
}
