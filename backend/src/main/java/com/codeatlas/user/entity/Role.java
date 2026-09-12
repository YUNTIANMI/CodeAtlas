package com.codeatlas.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 系统角色实体，对应 roles 表。
 *
 * <p>注意区分：此处为平台级角色（ROLE_USER / ROLE_ADMIN）；
 * 项目内的 OWNER / ADMIN / MEMBER / VIEWER 见 project_members 表。
 */
@Entity
@Table(name = "roles")
public class Role {

    public static final String ROLE_USER = "ROLE_USER";

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String name;

    @Column(length = 100)
    private String description;

    public Role() {
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
