package com.codeatlas.project;

import com.codeatlas.common.BusinessException;
import com.codeatlas.common.ErrorCode;
import com.codeatlas.project.entity.ProjectMember;
import com.codeatlas.project.entity.ProjectRole;
import com.codeatlas.project.repository.ProjectMemberRepository;
import com.codeatlas.project.service.ProjectPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 项目权限服务测试：这是本项目安全模型的核心，覆盖权限矩阵与越权隔离。
 *
 * <p>对应 docs/development.md 第 12 节（测试）要求的
 * 「正常流程 / 异常流程 / 权限流程 / 边界情况」，以及第 13 节（安全）中
 * 「用户 A 能否访问用户 B 的项目」这一关键场景。
 *
 * <p>权限矩阵见 docs/database.md 4.4 节。
 */
@ExtendWith(MockitoExtension.class)
class ProjectPermissionServiceTest {

    @Mock
    private ProjectMemberRepository memberRepository;

    private ProjectPermissionService permissionService;

    private static final Long PROJECT_ID = 10L;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        permissionService = new ProjectPermissionService(memberRepository);
    }

    /** 构造「该用户是项目成员」的场景。 */
    private void givenMember(Long userId, ProjectRole role) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(PROJECT_ID);
        member.setUserId(userId);
        member.setRole(role);
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, userId))
                .thenReturn(Optional.of(member));
    }

    /** 构造「该用户不是项目成员」的场景。 */
    private void givenNotMember(Long userId) {
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, userId))
                .thenReturn(Optional.empty());
    }

    private void assertInsufficientPermission(Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(ErrorCode.INSUFFICIENT_PERMISSION, ex.getErrorCode());
    }

    // ---------------- requireMember：最基本的一道门 ----------------

    @Test
    @DisplayName("requireMember：成员通过并返回成员关系")
    void requireMemberAllowed() {
        givenMember(USER_ID, ProjectRole.VIEWER);

        ProjectMember member = permissionService.requireMember(PROJECT_ID, USER_ID);

        assertEquals(ProjectRole.VIEWER, member.getRole());
        verify(memberRepository).findByProjectIdAndUserId(PROJECT_ID, USER_ID);
    }

    @Test
    @DisplayName("requireMember：非成员抛出 2002")
    void requireMemberNotMember() {
        givenNotMember(USER_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.requireMember(PROJECT_ID, USER_ID));
        assertEquals(ErrorCode.NOT_PROJECT_MEMBER, ex.getErrorCode());
    }

    // ---------------- requireManager：仅 OWNER / ADMIN ----------------

    @ParameterizedTest
    @EnumSource(value = ProjectRole.class, names = {"OWNER", "ADMIN"})
    @DisplayName("requireManager：OWNER / ADMIN 通过")
    void requireManagerAllowed(ProjectRole role) {
        givenMember(USER_ID, role);

        assertEquals(role, permissionService.requireManager(PROJECT_ID, USER_ID).getRole());
    }

    @ParameterizedTest
    @EnumSource(value = ProjectRole.class, names = {"MEMBER", "VIEWER"})
    @DisplayName("requireManager：MEMBER / VIEWER 被拒")
    void requireManagerDenied(ProjectRole role) {
        givenMember(USER_ID, role);

        assertInsufficientPermission(() -> permissionService.requireManager(PROJECT_ID, USER_ID));
    }

    // ---------------- requireWriter：OWNER / ADMIN / MEMBER ----------------

    @ParameterizedTest
    @EnumSource(value = ProjectRole.class, names = {"OWNER", "ADMIN", "MEMBER"})
    @DisplayName("requireWriter：OWNER / ADMIN / MEMBER 通过")
    void requireWriterAllowed(ProjectRole role) {
        givenMember(USER_ID, role);

        assertEquals(role, permissionService.requireWriter(PROJECT_ID, USER_ID).getRole());
    }

    @Test
    @DisplayName("requireWriter：VIEWER 只读，被拒")
    void requireWriterDenied() {
        givenMember(USER_ID, ProjectRole.VIEWER);

        assertInsufficientPermission(() -> permissionService.requireWriter(PROJECT_ID, USER_ID));
    }

    // ---------------- requireOwner：仅 OWNER ----------------

    @Test
    @DisplayName("requireOwner：OWNER 通过")
    void requireOwnerAllowed() {
        givenMember(USER_ID, ProjectRole.OWNER);

        assertEquals(ProjectRole.OWNER, permissionService.requireOwner(PROJECT_ID, USER_ID).getRole());
    }

    @ParameterizedTest
    @EnumSource(value = ProjectRole.class, names = {"ADMIN", "MEMBER", "VIEWER"})
    @DisplayName("requireOwner：ADMIN / MEMBER / VIEWER 均被拒（仅所有者可删除项目）")
    void requireOwnerDenied(ProjectRole role) {
        givenMember(USER_ID, role);

        assertInsufficientPermission(() -> permissionService.requireOwner(PROJECT_ID, USER_ID));
    }

    // ---------------- 越权隔离：安全的关键场景 ----------------

    @Test
    @DisplayName("越权隔离：用户 B 不是该项目成员，即使拥有其他项目也不可访问（返回 2002）")
    void crossUserIsolation() {
        // 用户 999 是另一个项目（PROJECT_ID=20）的 OWNER，但不是当前项目成员
        when(memberRepository.findByProjectIdAndUserId(PROJECT_ID, 999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionService.requireMember(PROJECT_ID, 999L));

        assertEquals(ErrorCode.NOT_PROJECT_MEMBER, ex.getErrorCode());
    }

    @Test
    @DisplayName("非成员在 requireManager / requireWriter / requireOwner 上均被拦在门外")
    void nonMemberBlockedEverywhere() {
        givenNotMember(USER_ID);

        assertThrows(BusinessException.class,
                () -> permissionService.requireManager(PROJECT_ID, USER_ID));
        assertThrows(BusinessException.class,
                () -> permissionService.requireWriter(PROJECT_ID, USER_ID));
        assertThrows(BusinessException.class,
                () -> permissionService.requireOwner(PROJECT_ID, USER_ID));
    }

    // ---------------- findRole：非强制查询 ----------------

    @Test
    @DisplayName("findRole：成员返回其角色")
    void findRolePresent() {
        givenMember(USER_ID, ProjectRole.ADMIN);

        Optional<ProjectRole> role = permissionService.findRole(PROJECT_ID, USER_ID);

        assertTrue(role.isPresent());
        assertEquals(ProjectRole.ADMIN, role.get());
    }

    @Test
    @DisplayName("findRole：非成员返回空，而不是抛异常")
    void findRoleAbsent() {
        givenNotMember(USER_ID);

        assertFalse(permissionService.findRole(PROJECT_ID, USER_ID).isPresent());
    }
}
