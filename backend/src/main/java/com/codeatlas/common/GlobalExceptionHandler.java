package com.codeatlas.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理：统一日志与响应格式。
 *
 * <p>重要错误必须包含：时间、请求路径、异常与堆栈，便于排查与审计。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：返回对应错误码。 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 | path={} | code={} | message={}",
                request.getRequestURI(), ex.getCode(), ex.getMessage());
        return Result.fail(ex.getErrorCode(), ex.getMessage());
    }

    /** 参数校验失败（@Valid）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException ex,
                                                  HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | path={} | message={}", request.getRequestURI(), message);
        return Result.fail(ErrorCode.BAD_REQUEST, message);
    }

    /** 缺少必填参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException ex) {
        return Result.fail(ErrorCode.BAD_REQUEST, "缺少参数：" + ex.getParameterName());
    }

    /** 请求体无法解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException ex) {
        return Result.fail(ErrorCode.BAD_REQUEST, "请求体格式错误");
    }

    /** 认证失败：401。 */
    @ExceptionHandler(AuthenticationException.class)
    public Result<Void> handleAuthenticationException(AuthenticationException ex,
                                                      HttpServletRequest request) {
        log.warn("认证失败 | path={} | message={}", request.getRequestURI(), ex.getMessage());
        return Result.fail(ErrorCode.UNAUTHORIZED);
    }

    /** 权限不足：403。 */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("权限不足 | path={} | message={}", request.getRequestURI(), ex.getMessage());
        return Result.fail(ErrorCode.FORBIDDEN);
    }

    /** 兜底：500。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex, HttpServletRequest request) {
        log.error("服务器内部错误 | path={} | method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    /** 兼容测试：部分场景直接以 HttpStatus 表达。 */
    protected HttpStatus resolveStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
