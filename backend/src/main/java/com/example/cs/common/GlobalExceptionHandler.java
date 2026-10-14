package com.example.cs.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：把各类异常统一转换为 {@link Result} 结构，
 * 避免把堆栈信息直接暴露给前端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：正常流程中的可预期失败（容量已满、时间冲突等）。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：@Valid 触发的 Bean Validation 错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败: {}", message);
        return Result.fail(ErrorCode.PARAM_ERROR, message.isEmpty() ? "参数校验失败" : message);
    }

    /** 请求体格式错误（如 JSON 不合法）。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.PARAM_ERROR, "请求体格式不正确");
    }

    /**
     * 数据库约束冲突。
     * 唯一索引是防重复选课的最后一道防线：并发下若两个请求同时通过了 Service 层校验，
     * 数据库会拒绝第二条插入，这里把它翻译成可读的业务提示。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        String root = e.getMostSpecificCause().getMessage();
        log.warn("数据完整性约束冲突: {}", root);
        if (root != null && root.contains("uk_student_course")) {
            return Result.fail(ErrorCode.ALREADY_SELECTED, "已选过该课程，请勿重复选课");
        }
        if (root != null && root.contains("chk_capacity")) {
            return Result.fail(ErrorCode.COURSE_FULL, "课程容量已满");
        }
        return Result.fail(ErrorCode.DUPLICATE_KEY, "数据唯一性冲突，请检查后重试");
    }

    /** 兜底异常。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR, "服务器内部错误：" + e.getMessage());
    }
}
