package com.example.cs.common;

import lombok.Data;

/**
 * 统一响应体。
 *
 * <p>约定：code = 0 表示成功，非 0 为业务错误码（见 {@link ErrorCode}）。
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS, "ok", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS, "ok", data);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(ErrorCode.SYSTEM_ERROR, message, null);
    }
}
