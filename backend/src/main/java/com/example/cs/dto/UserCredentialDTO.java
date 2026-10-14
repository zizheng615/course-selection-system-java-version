package com.example.cs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员修改账号登录名 / 密码的入参。
 */
@Data
public class UserCredentialDTO {

    @NotBlank(message = "登录账号不能为空")
    @Size(min = 3, max = 50, message = "登录账号长度需在 3-50 之间")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$",
            message = "登录账号只能包含字母、数字、下划线、点或短横线")
    private String username;

    /**
     * 新密码。留空表示只改账号、不动密码；
     * 填了则必须是 6-32 位。
     */
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 之间")
    private String password;
}
