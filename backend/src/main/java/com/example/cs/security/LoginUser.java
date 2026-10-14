package com.example.cs.security;

/**
 * 当前登录用户（从 JWT 解析而来）。
 *
 * @param userId   sys_user.id
 * @param username 登录账号
 * @param role     STUDENT / TEACHER / ADMIN
 * @param realName 真实姓名
 * @param refId    业务表主键：学生为 student.id，教师为 teacher.id，管理员为 null
 */
public record LoginUser(Long userId, String username, String role, String realName, Long refId) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isStudent() {
        return "STUDENT".equals(role);
    }

    public boolean isTeacher() {
        return "TEACHER".equals(role);
    }
}
