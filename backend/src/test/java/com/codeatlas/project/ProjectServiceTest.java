package com.codeatlas.project;

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
import com.codeatlas.project.service.ProjectPermissionService;
import com.codeatlas.project.service.ProjectService;
import com.codeatlas.user.entity.User;
import com.codeatlas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目服务单元测试：覆盖正常流程、异常流程与权限校验。
 *
 * <p>权限矩阵见 docs/database.md 4.4 节。
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository memberRepository;

    @Mock
    private ProjectPermissionService permissionService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService projectService;

    private static final Long OWNER_ID = 1L;

    private static final Long PROJECT_ID = 10L;

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(PROJECT_ID);
        project.setOwnerId(OWNER_ID);
        project.setName("个人项目管理平台");
        project.setDescription("基于 Spring Boot 与 React");
        project.setProjectType("WEB");
        project.setTechStack("Java,Spring Boot,React");
        project.setStatus(Project.STATUS_ACTIVE);
        project.setDeleted(false);
    }

    private ProjectMember member(Long userId, ProjectRole role) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(PROJECT_ID);
        member.setUserId(userId);
        member.setRole(role);
        return member;
    }

    @Test
    @DisplayName("创建项目：保存项目并自动登记创建者为 OWNER")
    void createProject() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("个人项目管理平台");
        request.setDescription("基于 Spring Boot 与 React");
        request.setProjectType("WEB");
        request.setTechStack(List.of("Java", "Spring Boot", "React"));

        when(projectRepository.save(any(Project.class))).thenReturn(project);

        ProjectVO result = projectService.create(OWNER_ID, request);

        assertNotNull(result);
        assertEquals("个人项目管理平台", result.getName());
        assertEquals(ProjectRole.OWNER, result.getMyRole());
        assertEquals(List.of("Java", "Spring Boot", "React"), result.getTechStack());
        verify(memberRepository).save(any(ProjectMember.class));
    }

    @Test
    @DisplayName("创建项目失败：名称为空")
    void createProjectWithBlankName() {
        CreateProjectRequest request = new CreateProjectRequest();
        request.setName("   ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.create(OWNER_ID, request));
        assertEquals(ErrorCode.PROJECT_NAME_EMPTY, ex.getErrorCode());
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("查看项目失败：项目不存在")
    void getByIdNotFound() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.getById(PROJECT_ID, OWNER_ID));
        assertEquals(ErrorCode.PROJECT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("查看项目失败：非项目成员（越权访问）")
    void getByIdNotMember() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        doThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER))
                .when(permissionService).requireMember(PROJECT_ID, 999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.getById(PROJECT_ID, 999L));
        assertEquals(ErrorCode.NOT_PROJECT_MEMBER, ex.getErrorCode());
    }

    @Test
    @DisplayName("查看项目成功：项目成员可见")
    void getByIdAsMember() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireMember(PROJECT_ID, 2L))
                .thenReturn(member(2L, ProjectRole.VIEWER));

        ProjectVO result = projectService.getById(PROJECT_ID, 2L);

        assertEquals(PROJECT_ID, result.getId());
        assertEquals(ProjectRole.VIEWER, result.getMyRole());
    }

    @Test
    @DisplayName("修改项目失败：VIEWER 无管理权限")
    void updateWithoutManagePermission() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionService).requireManager(PROJECT_ID, 2L);

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("新名称");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.update(PROJECT_ID, 2L, request));
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("修改项目成功：ADMIN 可修改")
    void updateAsAdmin() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, 2L))
                .thenReturn(member(2L, ProjectRole.ADMIN));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("新名称");

        ProjectVO result = projectService.update(PROJECT_ID, 2L, request);

        assertEquals("新名称", result.getName());
        assertEquals(ProjectRole.ADMIN, result.getMyRole());
    }

    @Test
    @DisplayName("删除项目失败：ADMIN 无删除权限（仅 OWNER 可删）")
    void deleteAsAdminDenied() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION))
                .when(permissionService).requireOwner(PROJECT_ID, 2L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.delete(PROJECT_ID, 2L));
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("删除项目成功：OWNER 执行软删除")
    void deleteAsOwner() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireOwner(PROJECT_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, ProjectRole.OWNER));

        projectService.delete(PROJECT_ID, OWNER_ID);

        assertTrue(project.getDeleted());
        verify(projectRepository).save(project);
    }

    @Test
    @DisplayName("邀请成员成功")
    void inviteMember() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, ProjectRole.OWNER));
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, 2L)).thenReturn(false);

        User target = new User();
        target.setId(2L);
        target.setUsername("bob");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(memberRepository.save(any(ProjectMember.class))).thenAnswer(invocation -> {
            ProjectMember saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        ProjectMemberVO result = projectService.inviteMember(PROJECT_ID, OWNER_ID, 2L, ProjectRole.MEMBER);

        assertEquals("bob", result.getUsername());
        assertEquals(ProjectRole.MEMBER, result.getRole());
    }

    @Test
    @DisplayName("邀请成员失败：该用户已是成员")
    void inviteExistingMember() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, ProjectRole.OWNER));
        when(memberRepository.existsByProjectIdAndUserId(PROJECT_ID, 2L)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.inviteMember(PROJECT_ID, OWNER_ID, 2L, ProjectRole.MEMBER));
        assertEquals(ErrorCode.MEMBER_ALREADY_EXISTS, ex.getErrorCode());
        verify(memberRepository, never()).save(any(ProjectMember.class));
    }

    @Test
    @DisplayName("邀请成员失败：不允许直接邀请为 OWNER")
    void inviteAsOwnerDenied() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, ProjectRole.OWNER));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.inviteMember(PROJECT_ID, OWNER_ID, 2L, ProjectRole.OWNER));
        assertEquals(ErrorCode.CANNOT_MODIFY_OWNER, ex.getErrorCode());
    }

    @Test
    @DisplayName("修改成员角色失败：不能修改 OWNER 的角色")
    void updateOwnerRoleDenied() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, 2L))
                .thenReturn(member(2L, ProjectRole.ADMIN));
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .thenReturn(Optional.of(member(OWNER_ID, ProjectRole.OWNER)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.updateMemberRole(PROJECT_ID, 2L, OWNER_ID, ProjectRole.MEMBER));
        assertEquals(ErrorCode.CANNOT_MODIFY_OWNER, ex.getErrorCode());
    }

    @Test
    @DisplayName("移除成员失败：不能移除 OWNER")
    void removeOwnerDenied() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, 2L))
                .thenReturn(member(2L, ProjectRole.ADMIN));
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, OWNER_ID))
                .thenReturn(Optional.of(member(OWNER_ID, ProjectRole.OWNER)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> projectService.removeMember(PROJECT_ID, 2L, OWNER_ID));
        assertEquals(ErrorCode.CANNOT_REMOVE_OWNER, ex.getErrorCode());
        verify(memberRepository, never()).delete(any(ProjectMember.class));
    }

    @Test
    @DisplayName("移除成员成功")
    void removeMember() {
        ProjectMember target = member(2L, ProjectRole.MEMBER);
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireManager(PROJECT_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, ProjectRole.OWNER));
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, 2L))
                .thenReturn(Optional.of(target));

        projectService.removeMember(PROJECT_ID, OWNER_ID, 2L);

        verify(memberRepository).delete(target);
    }

    @Test
    @DisplayName("成员列表：非成员不能查看")
    void listMembersDenied() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        doThrow(new BusinessException(ErrorCode.NOT_PROJECT_MEMBER))
                .when(permissionService).requireMember(PROJECT_ID, 999L);

        assertThrows(BusinessException.class,
                () -> projectService.listMembers(PROJECT_ID, 999L));
    }

    @Test
    @DisplayName("成员列表：成员可查看，且带用户名")
    void listMembers() {
        when(projectRepository.findActiveById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(permissionService.requireMember(PROJECT_ID, OWNER_ID))
                .thenReturn(member(OWNER_ID, ProjectRole.OWNER));
        when(memberRepository.findByProjectId(PROJECT_ID))
                .thenReturn(List.of(member(OWNER_ID, ProjectRole.OWNER),
                        member(2L, ProjectRole.MEMBER)));

        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setUsername("alice");
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        List<ProjectMemberVO> result = projectService.listMembers(PROJECT_ID, OWNER_ID);

        assertEquals(2, result.size());
        assertEquals("alice", result.get(0).getUsername());
        assertEquals(ProjectRole.OWNER, result.get(0).getRole());
        assertEquals("bob", result.get(1).getUsername());
    }
}
