package com.codeatlas.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常转换测试。
 *
 * <p>对应 docs/development.md 第 13 节（Phase 11：安全）中的「敏感数据」检查项：
 * 错误响应必须带上正确的 HTTP 状态码，且不得把服务端异常细节泄露给调用方。
 */
class GlobalExceptionHandlerTest {

    private static final String PATH = "/api/v1/projects/10/documents";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        return request;
    }

    /** 供构造 MethodArgumentNotValidException 使用的占位方法。 */
    @SuppressWarnings("unused")
    private void dummy(String parameter) {
        // no-op
    }

    @Test
    @DisplayName("业务异常：越权访问他人项目返回 403，并保留业务错误码 2002")
    void businessExceptionNotProjectMember() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.NOT_PROJECT_MEMBER), request());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.NOT_PROJECT_MEMBER.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("业务异常：限流返回 429")
    void businessExceptionTooManyRequests() {
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后再试"),
                request());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("登录失败次数过多，请稍后再试", response.getBody().getMessage());
    }

    @Test
    @DisplayName("认证失败：返回 401")
    void authenticationException() {
        ResponseEntity<Result<Void>> response = handler.handleAuthenticationException(
                new BadCredentialsException("bad credentials"), request());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("权限不足：返回 403")
    void accessDenied() {
        ResponseEntity<Result<Void>> response = handler.handleAccessDenied(
                new AccessDeniedException("denied"), request());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("参数校验失败：返回 400 并给出字段提示")
    void validationException() throws Exception {
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("dummy", String.class), 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "dto");
        bindingResult.addError(new FieldError("dto", "username", "用户名不能为空"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Result<Void>> response =
                handler.handleValidationException(ex, request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("username 用户名不能为空", response.getBody().getMessage());
    }

    @Test
    @DisplayName("未预期异常：返回 500 且不泄露异常细节")
    void unexpectedException() {
        ResponseEntity<Result<Void>> response = handler.handleException(
                new IllegalStateException("jdbc:mysql://user:pwd@127.0.0.1:3306/codeatlas"),
                request());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
        assertNull(response.getBody().getData());
        // 内部堆栈细节不得出现在响应体中
        assertEquals("服务器内部错误", response.getBody().getMessage());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("错误码与 HTTP 状态码映射符合 REST 语义")
    @CsvSource({
            "BAD_REQUEST,              400",
            "FILE_TOO_LARGE,           400",
            "UNSUPPORTED_FILE_TYPE,    400",
            "UNAUTHORIZED,             401",
            "INVALID_CREDENTIALS,      401",
            "FORBIDDEN,                403",
            "NOT_PROJECT_MEMBER,       403",
            "INSUFFICIENT_PERMISSION,  403",
            "NOT_FOUND,                404",
            "PROJECT_NOT_FOUND,        404",
            "USERNAME_EXISTS,          409",
            "TOO_MANY_REQUESTS,        429",
            "INTERNAL_ERROR,           500",
            "AI_SERVICE_ERROR,         500"
    })
    void errorCodeMapsToHttpStatus(ErrorCode errorCode, int expectedStatus) {
        assertEquals(expectedStatus, GlobalExceptionHandler.toHttpStatus(errorCode).value());
    }

    @ParameterizedTest
    @DisplayName("任何错误码都不会返回 2xx，避免失败被当成成功")
    @EnumSource(value = ErrorCode.class, names = "SUCCESS", mode = EnumSource.Mode.EXCLUDE)
    void everyErrorCodeMapsToErrorStatus(ErrorCode errorCode) {
        HttpStatus status = GlobalExceptionHandler.toHttpStatus(errorCode);

        assertNotNull(status);
        assertTrue(status.is4xxClientError() || status.is5xxServerError(),
                "错误码 " + errorCode + " 被映射为 " + status + "，应当为 4xx/5xx");
    }
}
