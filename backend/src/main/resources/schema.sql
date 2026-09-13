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

-- Phase 3 项目管理
CREATE TABLE IF NOT EXISTS projects (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id      BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(1000) DEFAULT NULL,
    project_type  VARCHAR(50)   DEFAULT NULL,
    tech_stack    VARCHAR(500)  DEFAULT NULL,
    status        TINYINT      NOT NULL DEFAULT 1,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_projects_owner_id (owner_id),
    KEY idx_projects_deleted (deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目表';

CREATE TABLE IF NOT EXISTS project_members (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    project_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    joined_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_members (project_id, user_id),
    KEY idx_project_members_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目成员表';

-- Phase 4 文档与代码管理
CREATE TABLE IF NOT EXISTS documents (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    project_id    BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    file_type     VARCHAR(20)  NOT NULL,
    file_size     BIGINT       DEFAULT NULL,
    storage_path  VARCHAR(500) NOT NULL,
    version       INT          NOT NULL DEFAULT 1,
    uploaded_by   BIGINT       DEFAULT NULL,
    indexed       TINYINT      NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_documents_project_id (project_id),
    KEY idx_documents_indexed (indexed)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目文档表';

CREATE TABLE IF NOT EXISTS code_files (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    language    VARCHAR(20)  DEFAULT NULL,
    content     LONGTEXT     DEFAULT NULL,
    file_size   BIGINT       DEFAULT NULL,
    commit_hash VARCHAR(64)  DEFAULT NULL,
    version     INT          NOT NULL DEFAULT 1,
    indexed     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_files_path (project_id, file_path),
    KEY idx_code_files_language (language),
    KEY idx_code_files_indexed (indexed)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '源代码文件表';

CREATE TABLE IF NOT EXISTS file_chunks (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    project_id   BIGINT      NOT NULL,
    source_type  VARCHAR(20) NOT NULL,
    source_id    BIGINT      NOT NULL,
    chunk_index  INT         NOT NULL,
    content      TEXT        NOT NULL,
    indexed      TINYINT     NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_file_chunks_source (source_type, source_id),
    KEY idx_file_chunks_project (project_id, indexed)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '文件切分块表';

-- Phase 6 AI 项目问答
CREATE TABLE IF NOT EXISTS conversations (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    project_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    title       VARCHAR(255) DEFAULT NULL,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_conversations_project_user (project_id, user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'AI 会话表';

CREATE TABLE IF NOT EXISTS messages (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT      NOT NULL,
    role             VARCHAR(20) NOT NULL,
    content          TEXT        NOT NULL,
    model            VARCHAR(50) DEFAULT NULL,
    token_count      INT         DEFAULT NULL,
    created_at       DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_messages_conversation (conversation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '会话消息表';

CREATE TABLE IF NOT EXISTS citations (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    message_id   BIGINT      NOT NULL,
    source_type  VARCHAR(20) NOT NULL,
    source_id    BIGINT      NOT NULL,
    file_name    VARCHAR(500) DEFAULT NULL,
    chunk_index  INT          DEFAULT NULL,
    score        DOUBLE       DEFAULT NULL,
    snippet      VARCHAR(500) DEFAULT NULL,
    created_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_citations_message (message_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '回答引用来源表';

CREATE TABLE IF NOT EXISTS ai_execution_logs (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    project_id         BIGINT      DEFAULT NULL,
    user_id            BIGINT      DEFAULT NULL,
    request_type       VARCHAR(50) DEFAULT NULL,
    model              VARCHAR(50) DEFAULT NULL,
    tools_used         TEXT        DEFAULT NULL,
    retrieved_content  TEXT        DEFAULT NULL,
    prompt_tokens      INT         DEFAULT NULL,
    completion_tokens  INT         DEFAULT NULL,
    execution_time_ms  INT         DEFAULT NULL,
    success            TINYINT     DEFAULT NULL,
    error_message      TEXT        DEFAULT NULL,
    result             TEXT        DEFAULT NULL,
    created_at         DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ai_logs_project (project_id),
    KEY idx_ai_logs_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'AI 执行日志表';

-- Phase 7 AI Code Review
CREATE TABLE IF NOT EXISTS review_results (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    project_id   BIGINT      NOT NULL,
    user_id      BIGINT      DEFAULT NULL,
    source_type  VARCHAR(20) DEFAULT NULL,
    source_ref   VARCHAR(500) DEFAULT NULL,
    severity     VARCHAR(20) DEFAULT NULL,
    category     VARCHAR(50) DEFAULT NULL,
    file_path    VARCHAR(500) DEFAULT NULL,
    line         INT         DEFAULT NULL,
    description  TEXT        DEFAULT NULL,
    risk         TEXT        DEFAULT NULL,
    suggestion   TEXT        DEFAULT NULL,
    model        VARCHAR(50) DEFAULT NULL,
    created_at   DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_project (project_id),
    KEY idx_review_severity (severity)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '代码审查结果表';

-- Phase 8 Git 分析
CREATE TABLE IF NOT EXISTS git_repositories (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    project_id        BIGINT      NOT NULL,
    repo_url          VARCHAR(500) NOT NULL,
    provider          VARCHAR(20)  DEFAULT NULL,
    full_name         VARCHAR(200) DEFAULT NULL,
    default_branch    VARCHAR(100) DEFAULT NULL,
    access_token_ref  VARCHAR(255) DEFAULT NULL,
    last_synced_at    DATETIME     DEFAULT NULL,
    sync_status       VARCHAR(20)  DEFAULT NULL,
    created_at        DATETIME    NOT NULL,
    updated_at        DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_git_repo_project (project_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Git 仓库配置表';

CREATE TABLE IF NOT EXISTS git_commits (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    repo_id       BIGINT      NOT NULL,
    commit_hash   VARCHAR(64) NOT NULL,
    message       TEXT        DEFAULT NULL,
    author_name   VARCHAR(100) DEFAULT NULL,
    author_email  VARCHAR(100) DEFAULT NULL,
    committed_at  DATETIME     DEFAULT NULL,
    additions     INT          DEFAULT NULL,
    deletions     INT          DEFAULT NULL,
    diff_content  LONGTEXT     DEFAULT NULL,
    summary       TEXT         DEFAULT NULL,
    analyzed      TINYINT     NOT NULL DEFAULT 0,
    created_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_git_commits_hash (commit_hash),
    KEY idx_git_commits_repo (repo_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Git 提交记录表';

-- Phase 9 Agent 工具调用记录
CREATE TABLE IF NOT EXISTS agent_tool_calls (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    project_id         BIGINT      NOT NULL,
    user_id            BIGINT      DEFAULT NULL,
    task               TEXT        DEFAULT NULL,
    tool_name          VARCHAR(50) NOT NULL,
    input_content      TEXT        DEFAULT NULL,
    output_content     TEXT        DEFAULT NULL,
    execution_time_ms  INT         DEFAULT NULL,
    success            TINYINT     NOT NULL DEFAULT 1,
    error_message      TEXT        DEFAULT NULL,
    step_index         INT         DEFAULT NULL,
    created_at         DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_agent_calls_project (project_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 工具调用记录表';

-- 初始化内置角色
INSERT IGNORE INTO roles (id, name, description) VALUES (1, 'ROLE_USER', '普通用户');
INSERT IGNORE INTO roles (id, name, description) VALUES (2, 'ROLE_ADMIN', '平台管理员');
