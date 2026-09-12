-- ============================================================
-- CodeAtlas 数据库初始化脚本
-- Phase 2 用户系统所需表：users / roles / user_roles
-- 其余表将在后续阶段按需追加
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    username       VARCHAR(50)  NOT NULL,
    email          VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    display_name   VARCHAR(50)  DEFAULT NULL,
    avatar_url     VARCHAR(255) DEFAULT NULL,
    status         TINYINT      NOT NULL DEFAULT 1,
    last_login_at  DATETIME     DEFAULT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户表';

CREATE TABLE IF NOT EXISTS roles (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32) NOT NULL,
    description VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统角色表';

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role_id (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户角色关联表';

-- 初始化内置角色
INSERT IGNORE INTO roles (id, name, description) VALUES (1, 'ROLE_USER', '普通用户');
INSERT IGNORE INTO roles (id, name, description) VALUES (2, 'ROLE_ADMIN', '平台管理员');
