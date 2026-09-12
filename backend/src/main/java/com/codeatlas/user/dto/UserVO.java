package com.codeatlas.user.dto;

import com.codeatlas.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象：对外暴露的用户信息，绝不包含 passwordHash。
 */
public class UserVO {

    private Long id;

    private String username;

    private String email;

    private String displayName;

    private String avatarUrl;

    private List<String> roles;

    private LocalDateTime createdAt;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setDisplayName(user.getDisplayName());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setCreatedAt(user.getCreatedAt());
        if (user.getRoles() != null) {
            vo.setRoles(user.getRoles().stream()
                    .map(role -> role.getName())
                    .toList());
        }
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
