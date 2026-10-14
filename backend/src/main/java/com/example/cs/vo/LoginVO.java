package com.example.cs.vo;

import com.example.cs.security.LoginUser;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录返回：Token + 前端需要的用户信息（用于渲染菜单与路由守卫）。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;
    private Long userId;
    private String username;
    private String role;
    private String realName;
    /** 学生为 student.id，教师为 teacher.id，管理员为 null */
    private Long refId;
    private Long expireHours;

    /** expireHours 用包装类型：查询当前用户时不需要返回该字段，传 null */
    public static LoginVO of(String token, LoginUser user, Long expireHours) {
        return new LoginVO(token, user.userId(), user.username(), user.role(),
                user.realName(), user.refId(), expireHours);
    }
}
