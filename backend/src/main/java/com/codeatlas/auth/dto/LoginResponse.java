package com.codeatlas.auth.dto;

import com.codeatlas.user.dto.UserVO;

/**
 * 登录响应：Token + 有效期 + 用户信息。
 */
public class LoginResponse {

    private String token;

    private String tokenType = "Bearer";

    private long expiresIn;

    private UserVO user;

    public LoginResponse() {
    }

    public LoginResponse(String token, long expiresIn, UserVO user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }
}
