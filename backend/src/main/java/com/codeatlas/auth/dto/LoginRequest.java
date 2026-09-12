package com.codeatlas.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。username 字段允许填写用户名或邮箱。
 */
public class LoginRequest {

    @NotBlank(message = "用户名或邮箱不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
