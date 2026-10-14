package com.example.cs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生新增 / 修改入参。
 */
@Data
public class StudentDTO {

    @NotBlank(message = "学号不能为空")
    @Size(max = 20, message = "学号长度不能超过 20")
    private String studentNo;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50")
    private String name;

    @Pattern(regexp = "^[MF]$", message = "性别只能为 M 或 F")
    private String gender = "M";

    @NotBlank(message = "专业不能为空")
    private String major;

    @NotBlank(message = "年级不能为空")
    private String grade;

    @Size(max = 20, message = "电话长度不能超过 20")
    private String phone;

    /**
     * 新增学生时可选：若不传则不创建登录账号。
     */
    private String username;

    /** 新增时的初始密码，不传默认为 123456 */
    private String password;
}
