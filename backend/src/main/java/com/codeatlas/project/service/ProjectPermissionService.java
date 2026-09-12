package com.codeatlas.project.service;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.project.entity.ProjectMember;
import com.codeatlas.project.entity.ProjectRole;
import com.codeatlas.project.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 项目权限校验。
 *
 * <p>这是本项目安全模型的核心：所有涉及 projectId 的操作都必须先经过这里，
 * 严禁仅凭请求参数中的 projectId 直接查询数据。
 *
 * <p>权限矩阵见 docs/database.md 4.4 节。
 */
@Service
public class ProjectPermissionService {

    private final ProjectMemberRepository memberRepository;

    public ProjectPermissionService(ProjectMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** 查询成员关系，非成员时抛出 403。 */
    public ProjectMember requireMember(Long projectId, Long userId) {
        return memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PROJECT_MEMBER));
    }

    /** 要求具备管理权限（OWNER / ADMIN）：修改配置、管理成员。 */
    public ProjectMember requireManager(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (!member.getRole().canManage()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
        return member;
    }

    /** 要求具备写入权限（OWNER / ADMIN / MEMBER）：上传文档与代码、创建分析任务。 */
    public ProjectMember requireWriter(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (!member.getRole().canWrite()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
        return member;
    }

    /** 要求具备所有者权限（OWNER）：仅删除项目需要。 */
    public ProjectMember requireOwner(Long projectId, Long userId) {
        ProjectMember member = requireMember(projectId, userId);
        if (member.getRole() != ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
        return member;
    }

    /** 非强制性查询：返回成员角色，非成员返回空。 */
    public Optional<ProjectRole> findRole(Long projectId, Long userId) {
        return memberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMember::getRole);
    }
}
