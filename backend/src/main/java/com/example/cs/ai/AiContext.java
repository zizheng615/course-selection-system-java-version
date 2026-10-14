package com.example.cs.ai;

/**
 * 提问时的用户上下文。
 *
 * <p>同一个问题，学生和教务管理员关心的答案往往不同（例如「怎么加课」），
 * 因此知识库按角色过滤，Claude 的提示词里也会带上角色。
 *
 * @param role     STUDENT / TEACHER / ADMIN
 * @param realName 用户姓名，用于称呼
 */
public record AiContext(String role, String realName) {

    public String roleName() {
        return switch (role) {
            case "ADMIN" -> "教务管理员";
            case "TEACHER" -> "教师";
            case "STUDENT" -> "学生";
            default -> "用户";
        };
    }
}
