package com.codeatlas.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理：统一日志、HTTP 状态码与响应格式。
 *
 * <p>安全要求（Phase 11）：错误响应必须同时体现在 <b>HTTP 状态码</b> 与响应体 {@code code} 上。
 * 只返回 200 + 业务错误码会让网关、浏览器与前端拦截器无法识别认证/越权失败，
 * 因此这里显式把 {@link ErrorCode} 映射为 HTTP 状态。
 *
 * <p>响应体中保留业务错误码（如 2002 不是项目成员），HTTP 状态则使用标准语义码（403）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务错误码 → HTTP 状态码。未列出的错误码按 500 处理。 */
    private static final Map<ErrorCode, HttpStatus> STATUS_MAPPING = new EnumMap<>(ErrorCode.class);

    static {
        // 400：参数/输入问题
        for (ErrorCode code : new ErrorCode[]{
                ErrorCode.BAD_REQUEST, ErrorCode.PASSWORD_TOO_WEAK,
                ErrorCode.PROJECT_NAME_EMPTY, ErrorCode.FILE_EMPTY,
                ErrorCode.UNSUPPORTED_FILE_TYPE, ErrorCode.FILE_TOO_LARGE,
                ErrorCode.FILE_PARSE_FAILED, ErrorCode.FILE_STORAGE_FAILED,
                ErrorCode.MESSAGE_EMPTY, ErrorCode.INVALID_REPO_URL,
                ErrorCode.REVIEW_PARSE_FAILED}) {
            STATUS_MAPPING.put(code, HttpStatus.BAD_REQUEST);
        }
        // 401：认证失败
        for (ErrorCode code : new ErrorCode[]{
                ErrorCode.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                ErrorCode.USER_DISABLED}) {
            STATUS_MAPPING.put(code, HttpStatus.UNAUTHORIZED);
        }
        // 403：权限不足（含越权访问他人项目）
        for (ErrorCode code : new ErrorCode[]{
                ErrorCode.FORBIDDEN, ErrorCode.NOT_PROJECT_MEMBER,
                ErrorCode.INSUFFICIENT_PERMISSION, ErrorCode.CANNOT_MODIFY_OWNER,
                ErrorCode.CANNOT_REMOVE_OWNER}) {
            STATUS_MAPPING.put(code, HttpStatus.FORBIDDEN);
        }
        // 404：资源不存在
        for (ErrorCode code : new ErrorCode[]{
                ErrorCode.NOT_FOUND, ErrorCode.USER_NOT_FOUND,
                ErrorCode.PROJECT_NOT_FOUND, ErrorCode.MEMBER_NOT_FOUND,
                ErrorCode.DOCUMENT_NOT_FOUND, ErrorCode.CODE_FILE_NOT_FOUND,
                ErrorCode.CONVERSATION_NOT_FOUND, ErrorCode.REVIEW_NOT_FOUND,
                ErrorCode.GIT_REPO_NOT_FOUND, ErrorCode.GIT_COMMIT_NOT_FOUND}) {
            STATUS_MAPPING.put(code, HttpStatus.NOT_FOUND);
        }
        // 409：冲突
        for (ErrorCode code : new ErrorCode[]{
                ErrorCode.CONFLICT, ErrorCode.USERNAME_EXISTS, ErrorCode.EMAIL_EXISTS,
                ErrorCode.MEMBER_ALREADY_EXISTS, ErrorCode.GIT_REPO_EXISTS}) {
            STATUS_MAPPING.put(code, HttpStatus.CONFLICT);
        }
        // 429：限流
        STATUS_MAPPING.put(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);
        // 500：服务端问题
        for (ErrorCode code : new ErrorCode[]{
                ErrorCode.INTERNAL_ERROR, ErrorCode.AI_SERVICE_ERROR,
                ErrorCode.AI_SERVICE_TIMEOUT, ErrorCode.VECTOR_STORE_UNAVAILABLE}) {
            STATUS_MAPPING.put(code, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** 业务异常：返回对应错误码与 HTTP 状态。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex,
                                                                HttpServletRequest request) {
        log.warn("业务异常 | path={} | code={} | message={}",
                request.getRequestURI(), ex.getCode(), ex.getMessage());
        return build(ex.getErrorCode(), ex.getMessage());
    }

    /** 参数校验失败（@Valid）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException ex,
                                                                  HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | path={} | message={}", request.getRequestURI(), message);
        return build(ErrorCode.BAD_REQUEST, message);
    }

    /** 缺少必填参数。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(ErrorCode.BAD_REQUEST, "缺少参数：" + ex.getParameterName());
    }

    /** 请求体无法解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.BAD_REQUEST, "请求体格式错误");
    }

    /** 认证失败：401。 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(AuthenticationException ex,
                                                                      HttpServletRequest request) {
        log.warn("认证失败 | path={} | message={}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.UNAUTHORIZED, null);
    }

    /** 权限不足：403。 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex,
                                                           HttpServletRequest request) {
        log.warn("权限不足 | path={} | message={}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.FORBIDDEN, null);
    }

    /** 兜底：500。对外只暴露通用信息，细节仅记入服务端日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex, HttpServletRequest request) {
        log.error("服务器内部错误 | path={} | method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return build(ErrorCode.INTERNAL_ERROR, null);
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        Result<Void> body = (message == null)
                ? Result.fail(errorCode)
                : Result.fail(errorCode, message);
        return ResponseEntity.status(toHttpStatus(errorCode)).body(body);
    }

    /** 错误码 → HTTP 状态码（包级可见，便于测试）。 */
    static HttpStatus toHttpStatus(ErrorCode errorCode) {
        return STATUS_MAPPING.getOrDefault(errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
