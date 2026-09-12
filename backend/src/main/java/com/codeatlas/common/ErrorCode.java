package com.codeatlas.common;

/**
 * 错误码定义，与 docs/api.md 的错误码表一一对应。
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或 Token 失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    /* 用户相关 1001-1099 */
    USERNAME_EXISTS(1001, "用户名已存在"),
    EMAIL_EXISTS(1002, "邮箱已被注册"),
    INVALID_CREDENTIALS(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),
    USER_NOT_FOUND(1005, "用户不存在"),
    PASSWORD_TOO_WEAK(1006, "密码强度不足，至少 8 位"),

    /* 项目相关 2001-2099 */
    PROJECT_NOT_FOUND(2001, "项目不存在"),
    NOT_PROJECT_MEMBER(2002, "不是该项目成员"),
    INSUFFICIENT_PERMISSION(2003, "权限不足，无法执行该操作"),
    MEMBER_ALREADY_EXISTS(2004, "该用户已是项目成员"),
    MEMBER_NOT_FOUND(2005, "成员不存在"),
    CANNOT_MODIFY_OWNER(2006, "不能修改项目所有者的角色"),
    CANNOT_REMOVE_OWNER(2007, "不能移除项目所有者"),
    PROJECT_NAME_EMPTY(2008, "项目名称不能为空"),

    /* 文件相关 3001-3099 */
    FILE_EMPTY(3001, "文件内容为空"),
    UNSUPPORTED_FILE_TYPE(3002, "不支持的文件类型"),
    FILE_TOO_LARGE(3003, "文件超过大小限制"),
    DOCUMENT_NOT_FOUND(3004, "文档不存在"),
    CODE_FILE_NOT_FOUND(3005, "代码文件不存在"),
    FILE_PARSE_FAILED(3006, "文件解析失败"),
    FILE_STORAGE_FAILED(3007, "文件存储失败"),

    /* AI 相关 5001-5099 */
    AI_SERVICE_ERROR(5001, "AI 服务调用失败"),
    AI_SERVICE_TIMEOUT(5002, "AI 服务超时"),
    VECTOR_STORE_UNAVAILABLE(5003, "向量库不可用");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
