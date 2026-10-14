package com.example.cs.vo;

import com.example.cs.entity.SysUser;
import com.example.cs.support.SqlUtils;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号视图对象。
 *
 * <p>把 sys_user 与其挂接的业务记录（student / teacher）拼在一起展示，
 * 让管理员一眼看清"这个账号是谁、对应哪条业务数据"。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String role;
    private String roleName;
    private String realName;
    private Integer status;
    private LocalDateTime createdAt;

    /** 关联的业务类型：学生 / 教师 / —（管理员无关联） */
    private String linkType;
    /** 学号 / 工号 */
    private String linkNo;
    /** 专业 / 院系 */
    private String linkExtra;

    public static UserVO of(SysUser u) {
        UserVO v = new UserVO();
        v.id = u.getId();
        v.username = u.getUsername();
        v.role = u.getRole();
        v.roleName = roleName(u.getRole());
        v.realName = u.getRealName();
        v.status = u.getStatus();
        v.createdAt = u.getCreatedAt();
        v.linkType = "—";
        return v;
    }

    /**
     * 对应 SysUserRepository#searchUsers 的列顺序：
     * id, username, role, real_name, status, created_at,
     * student_no, major, teacher_no, dept
     */
    public static UserVO ofRow(Object[] r) {
        UserVO v = new UserVO();
        v.id = SqlUtils.toLong(r[0]);
        v.username = SqlUtils.toStr(r[1]);
        v.role = SqlUtils.toStr(r[2]);
        v.roleName = roleName(v.role);
        v.realName = SqlUtils.toStr(r[3]);
        v.status = SqlUtils.toInt(r[4]);
        v.createdAt = SqlUtils.toDateTime(r[5]);

        String studentNo = SqlUtils.toStr(r[6]);
        String major = SqlUtils.toStr(r[7]);
        String teacherNo = SqlUtils.toStr(r[8]);
        String dept = SqlUtils.toStr(r[9]);

        if (studentNo != null) {
            v.linkType = "学生";
            v.linkNo = studentNo;
            v.linkExtra = major;
        } else if (teacherNo != null) {
            v.linkType = "教师";
            v.linkNo = teacherNo;
            v.linkExtra = dept;
        } else {
            v.linkType = "—";
        }
        return v;
    }

    private static String roleName(String role) {
        return switch (role) {
            case "ADMIN" -> "管理员";
            case "TEACHER" -> "教师";
            case "STUDENT" -> "学生";
            default -> role;
        };
    }
}
