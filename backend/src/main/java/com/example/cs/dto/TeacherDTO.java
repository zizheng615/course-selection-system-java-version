package com.example.cs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教师新增 / 修改入参。
 */
@Data
public class TeacherDTO {

    @NotBlank(message = "工号不能为空")
    @Size(max = 20, message = "工号长度不能超过 20")
    private String teacherNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String name;

    private String title;

    @NotBlank(message = "所属院系不能为空")
    private String dept;

    private String username;

    private String password;
}
