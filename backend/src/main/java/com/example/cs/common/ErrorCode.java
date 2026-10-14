package com.example.cs.common;

/**
 * 业务错误码约定。
 *
 * <pre>
 *      0        成功
 *   1000-1099   认证 / 权限
 *   2000-2999   参数校验失败
 *   3000-3999   资源不存在 / 状态非法
 *   4000-4999   业务规则拒绝（容量已满、时间冲突等）
 *   5000+       系统异常
 * </pre>
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    /** 成功 */
    public static final int SUCCESS = 0;

    // ---------- 认证 / 权限 ----------
    public static final int LOGIN_FAILED = 1001;
    public static final int TOKEN_INVALID = 1002;
    public static final int NO_PERMISSION = 1003;

    // ---------- 参数校验 ----------
    public static final int PARAM_ERROR = 2000;

    // ---------- 资源不存在 ----------
    public static final int NOT_FOUND = 3001;

    // ---------- 业务规则拒绝 ----------
    public static final int COURSE_CLOSED = 4001;
    public static final int ALREADY_SELECTED = 4002;
    public static final int TIME_CONFLICT = 4003;
    public static final int COURSE_FULL = 4004;
    public static final int NOT_SELECTED = 4005;
    public static final int COURSE_DELETE_FORBIDDEN = 4006;
    public static final int DUPLICATE_KEY = 4007;

    // ---------- 系统异常 ----------
    public static final int SYSTEM_ERROR = 5000;
}
