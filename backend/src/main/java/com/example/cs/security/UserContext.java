package com.example.cs.security;

import com.example.cs.common.BusinessException;
import com.example.cs.common.ErrorCode;

/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 * 由 {@link JwtInterceptor} 在请求进入时写入，请求结束时清理。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 可能为 null（如登录接口本身）。 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 必须已登录，否则抛 1001。 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "未登录或登录已过期");
        }
        return user;
    }

    /** 当前学生 id，非学生身份抛 1003。 */
    public static Long requireStudentId() {
        LoginUser user = require();
        if (!user.isStudent() || user.refId() == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该操作仅学生可用");
        }
        return user.refId();
    }

    /** 当前教师 id，非教师身份抛 1003。 */
    public static Long requireTeacherId() {
        LoginUser user = require();
        if (!user.isTeacher() || user.refId() == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该操作仅教师可用");
        }
        return user.refId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
