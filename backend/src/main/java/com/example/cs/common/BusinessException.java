package com.example.cs.common;

import lombok.Getter;

/**
 * 业务异常。由 Service 层抛出，统一被 {@link GlobalExceptionHandler} 捕获并转为 {@link Result}。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.SYSTEM_ERROR;
    }
}
