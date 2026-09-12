package com.codeatlas.project.entity;

/**
 * 项目内角色。
 *
 * <p>注意与系统角色（roles 表的 ROLE_USER / ROLE_ADMIN）区分：
 * 此处为项目级权限，作用域仅限单个项目。
 */
public enum ProjectRole {

    /** 项目所有者：唯一，拥有全部权限，可删除项目。 */
    OWNER,

    /** 管理员：可修改配置、管理成员，不可删除项目。 */
    ADMIN,

    /** 普通成员：可查看项目、上传文档与代码、创建分析任务。 */
    MEMBER,

    /** 观察者：仅可查看。 */
    VIEWER;

    /** 是否具备管理成员与修改配置的权限。 */
    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }

    /** 是否具备写入权限（上传文档/代码、创建分析任务）。 */
    public boolean canWrite() {
        return this == OWNER || this == ADMIN || this == MEMBER;
    }
}
