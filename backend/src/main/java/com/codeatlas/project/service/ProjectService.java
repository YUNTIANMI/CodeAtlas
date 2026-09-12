package com.codeatlas.project.service;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.project.dto.CreateProjectRequest;
import com.codeatlas.project.dto.ProjectMemberVO;
import com.codeatlas.project.dto.ProjectVO;
import com.codeatlas.project.dto.UpdateProjectRequest;
import com.codeatlas.project.entity.Project;
import com.codeatlas.project.entity.ProjectMember;
import com.codeatlas.project.entity.ProjectRole;
import com.codeatlas.project.repository.ProjectMemberRepository;
import com.codeatlas.project.repository.ProjectRepository;
import com.codeatlas.user.entity.User;
import com.codeatlas.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 项目服务：项目 CRUD 与成员管理。
 *
 * <p>所有方法均要求传入 operatorId（操作者），并在内部完成权限校验。
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;

    private final ProjectMemberRepository memberRepository;

    private final ProjectPermissionService permissionService;

    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          ProjectPermissionService permissionService,
                          UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    /** 创建项目，并将创建者自动登记为 OWNER。 */
    @Transactional
    public ProjectVO create(Long operatorId, CreateProjectRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PROJECT_NAME_EMPTY);
        }

        Project project = new Project();
        project.setOwnerId(operatorId);
        project.setName(request.getName().trim());
        project.setDescription(request.getDescription());
        project.setProjectType(request.getProjectType());
        project.setTechStack(joinTechStack(request.getTechStack()));
        project.setStatus(Project.STATUS_ACTIVE);
        project.setDeleted(false);

        Project saved = projectRepository.save(project);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(saved.getId());
        owner.setUserId(operatorId);
        owner.setRole(ProjectRole.OWNER);
        memberRepository.save(owner);

        log.info("项目创建成功 | projectId={} | ownerId={}", saved.getId(), operatorId);
        return ProjectVO.from(saved, ProjectRole.OWNER);
    }

    /** 查询当前用户参与的项目（含被邀请加入的）。 */
    @Transactional(readOnly = true)
    public List<ProjectVO> listMyProjects(Long operatorId) {
        List<Project> projects = projectRepository.findProjectsByMemberId(operatorId);
        List<ProjectVO> result = new ArrayList<>();
        for (Project project : projects) {
            ProjectRole role = permissionService.findRole(project.getId(), operatorId).orElse(null);
            result.add(ProjectVO.from(project, role));
        }
        return result;
    }

    /** 查看项目详情：必须是项目成员。 */
    @Transactional(readOnly = true)
    public ProjectVO getById(Long projectId, Long operatorId) {
        Project project = getActiveProject(projectId);
        ProjectMember member = permissionService.requireMember(projectId, operatorId);
        return ProjectVO.from(project, member.getRole());
    }

    /** 修改项目：OWNER 或 ADMIN。 */
    @Transactional
    public ProjectVO update(Long projectId, Long operatorId, UpdateProjectRequest request) {
        Project project = getActiveProject(projectId);
        ProjectMember member = permissionService.requireManager(projectId, operatorId);

        if (request.getName() != null && !request.getName().isBlank()) {
            project.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getProjectType() != null) {
            project.setProjectType(request.getProjectType());
        }
        if (request.getTechStack() != null) {
            project.setTechStack(joinTechStack(request.getTechStack()));
        }

        Project saved = projectRepository.save(project);
        log.info("项目更新成功 | projectId={} | operatorId={}", projectId, operatorId);
        return ProjectVO.from(saved, member.getRole());
    }

    /** 删除项目（软删除）：仅 OWNER。 */
    @Transactional
    public void delete(Long projectId, Long operatorId) {
        Project project = getActiveProject(projectId);
        permissionService.requireOwner(projectId, operatorId);

        project.setDeleted(true);
        projectRepository.save(project);
        log.info("项目删除成功 | projectId={} | operatorId={}", projectId, operatorId);
    }

    /** 成员列表：项目成员可见。 */
    @Transactional(readOnly = true)
    public List<ProjectMemberVO> listMembers(Long projectId, Long operatorId) {
        getActiveProject(projectId);
        permissionService.requireMember(projectId, operatorId);

        List<ProjectMember> members = memberRepository.findByProjectId(projectId);
        List<ProjectMemberVO> result = new ArrayList<>();
        for (ProjectMember member : members) {
            String username = userRepository.findById(member.getUserId())
                    .map(User::getUsername)
                    .orElse("未知用户");
            result.add(ProjectMemberVO.from(member, username));
        }
        return result;
    }

    /** 邀请成员：OWNER 或 ADMIN；不允许直接邀请为 OWNER。 */
    @Transactional
    public ProjectMemberVO inviteMember(Long projectId, Long operatorId, Long targetUserId,
                                        ProjectRole role) {
        getActiveProject(projectId);
        permissionService.requireManager(projectId, operatorId);

        if (role == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWNER);
        }
        if (memberRepository.existsByProjectIdAndUserId(projectId, targetUserId)) {
            throw new BusinessException(ErrorCode.MEMBER_ALREADY_EXISTS);
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(targetUserId);
        member.setRole(role);
        ProjectMember saved = memberRepository.save(member);

        log.info("成员邀请成功 | projectId={} | targetUserId={} | role={}", projectId, targetUserId, role);
        return ProjectMemberVO.from(saved, target.getUsername());
    }

    /** 修改成员角色：OWNER 或 ADMIN；不能修改 OWNER，也不能把他人提为 OWNER。 */
    @Transactional
    public ProjectMemberVO updateMemberRole(Long projectId, Long operatorId, Long targetUserId,
                                            ProjectRole newRole) {
        getActiveProject(projectId);
        permissionService.requireManager(projectId, operatorId);

        ProjectMember target = memberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (target.getRole() == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWNER);
        }
        if (newRole == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_OWNER);
        }

        target.setRole(newRole);
        ProjectMember saved = memberRepository.save(target);

        String username = userRepository.findById(targetUserId)
                .map(User::getUsername)
                .orElse("未知用户");
        log.info("成员角色修改成功 | projectId={} | targetUserId={} | newRole={}",
                projectId, targetUserId, newRole);
        return ProjectMemberVO.from(saved, username);
    }

    /** 移除成员：OWNER 或 ADMIN；不能移除 OWNER。 */
    @Transactional
    public void removeMember(Long projectId, Long operatorId, Long targetUserId) {
        getActiveProject(projectId);
        permissionService.requireManager(projectId, operatorId);

        ProjectMember target = memberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (target.getRole() == ProjectRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_OWNER);
        }

        memberRepository.delete(target);
        log.info("成员移除成功 | projectId={} | targetUserId={}", projectId, targetUserId);
    }

    private Project getActiveProject(Long projectId) {
        return projectRepository.findActiveById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private String joinTechStack(List<String> techStack) {
        if (techStack == null || techStack.isEmpty()) {
            return null;
        }
        return String.join(",", techStack);
    }
}
