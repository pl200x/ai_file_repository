package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.InvitationDTO;
import com.example.file_management.controller.dto.PermissionOperationDTO;
import com.example.file_management.controller.dto.RequestPermissionDTO;
import com.example.file_management.controller.vo.UserPermissionVO;
import com.example.file_management.entity.File;
import com.example.file_management.entity.User;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.integration.PermissionDTO;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.integration.vo.PermissionVO;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.service.KnowledgeRepositoryService;
import com.example.file_management.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionManagementServiceImplTest {

    @Mock
    private PermissionIntegration permissionIntegration;
    @Mock
    private UserService userService;
    @Mock
    private KnowledgeRepositoryService knowledgeRepositoryService;
    @Mock
    private FileMapper fileMapper;

    @InjectMocks
    private PermissionManagementServiceImpl permissionManagementService;

    @Test
    void queryPermissions_requiresManageableAndEnrichesUserData() {
        stubFileAndActor();
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 1, "MANAGEABLE")).thenReturn(true);
        PermissionVO permission = permission(
                7, 9, false, true, false, "APPROVED");
        when(permissionIntegration.getPermissionsByTarget("FILE", 42))
                .thenReturn(List.of(permission));
        when(userService.queryByIds(List.of(9)))
                .thenReturn(List.of(user(9, "Ada")));

        List<UserPermissionVO> result =
                permissionManagementService.queryPermissionsByTarget(
                        1, "file", 42);

        assertEquals(1, result.size());
        assertEquals(7, result.get(0).getPermissionId());
        assertEquals("Ada", result.get(0).getName());
        assertEquals("WRITABLE", result.get(0).getPermissionType());
        assertEquals("APPROVED", result.get(0).getStatus());
    }

    @Test
    void inviteWithoutConfirmation_givesThenApprovesPermission() {
        stubFileAndActor();
        when(userService.queryById(9)).thenReturn(user(9, "Ada"));
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 1, "MANAGEABLE")).thenReturn(true);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 9, "READABLE")).thenReturn(false);

        permissionManagementService.inviteUser(new InvitationDTO(
                1, 9, "FILE", 42, "READABLE", false, 86_400_000L));

        ArgumentCaptor<PermissionDTO> captor =
                ArgumentCaptor.forClass(PermissionDTO.class);
        verify(permissionIntegration)
                .givePermissionByUserID(captor.capture());
        assertEquals(9, captor.getValue().getUserId());
        assertEquals(42, captor.getValue().getTargetId());
        assertEquals("READABLE", captor.getValue().getPermission());
        assertEquals(86_400_000L, captor.getValue().getExpirationTime());
        verify(permissionIntegration).approvePermission("FILE", 42, 9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READABLE", "WRITABLE", "MANAGEABLE"})
    void selfRequest_supportsEveryPermissionLevel(
            String permissionLevel) {
        stubFileAndActor(9);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 9, permissionLevel)).thenReturn(false);

        permissionManagementService.requestPermission(
                new RequestPermissionDTO(
                        9, 9, "FILE", 42, permissionLevel,
                        259_200_000L));

        verify(permissionIntegration)
                .checkPermissionByTypeTargetUserId(
                        "FILE", 42, 9, permissionLevel);
        ArgumentCaptor<PermissionDTO> captor =
                ArgumentCaptor.forClass(PermissionDTO.class);
        verify(permissionIntegration)
                .givePermissionByUserID(captor.capture());
        assertEquals(permissionLevel, captor.getValue().getPermission());
    }

    @Test
    void managerCanInviteSameTenantUserFromAnotherRepository() {
        stubFileAndActor();
        when(userService.queryById(9))
                .thenReturn(user(9, "Other repository member"));
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 1, "MANAGEABLE")).thenReturn(true);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 9, "READABLE")).thenReturn(false);

        permissionManagementService.inviteUser(new InvitationDTO(
                1, 9, "FILE", 42, "READABLE", true,
                86_400_000L));

        verify(permissionIntegration)
                .givePermissionByUserID(any(PermissionDTO.class));
        verify(permissionIntegration, never())
                .approvePermission("FILE", 42, 9);
    }

    @Test
    void managerCannotInviteUserFromAnotherTenant() {
        stubFileAndActor();
        User crossTenantUser = user(9, "Outside");
        crossTenantUser.setTenantId(4);
        when(userService.queryById(9)).thenReturn(crossTenantUser);

        assertThrows(
                UserPermissionDeniedException.class,
                () -> permissionManagementService.inviteUser(
                        new InvitationDTO(
                                1, 9, "FILE", 42, "READABLE",
                                true, 86_400_000L)));

        verify(permissionIntegration, never())
                .givePermissionByUserID(any(PermissionDTO.class));
    }

    @Test
    void approvePermission_withoutManageablePermission_isDenied() {
        stubFileAndActor();
        when(userService.queryById(9)).thenReturn(user(9, "Ada"));
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 1, "MANAGEABLE")).thenReturn(false);

        assertThrows(
                UserPermissionDeniedException.class,
                () -> permissionManagementService.approvePermission(
                        new PermissionOperationDTO(1, 9, "FILE", 42)));

        verify(permissionIntegration, never())
                .approvePermission(anyString(), anyInt(), anyInt());
    }

    @Test
    void requestPermission_rejectsUnsupportedDuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> permissionManagementService.requestPermission(
                        new RequestPermissionDTO(
                                9, 9, "FILE", 42, "READABLE", 123L)));

        verify(permissionIntegration, never())
                .givePermissionByUserID(any(PermissionDTO.class));
    }

    private void stubFileAndActor() {
        stubFileAndActor(1);
    }

    private void stubFileAndActor(int actorId) {
        when(userService.queryById(actorId))
                .thenReturn(user(actorId, "Manager"));
        File file = new File();
        file.setId(42);
        file.setTenantId(3);
        when(fileMapper.queryById(42)).thenReturn(file);
    }

    private User user(int id, String name) {
        User user = new User();
        user.setId(id);
        user.setTenantId(3);
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setProfile("profile-" + id);
        return user;
    }

    private PermissionVO permission(
            int id,
            int userId,
            boolean readable,
            boolean writable,
            boolean manageable,
            String status) {
        return new PermissionVO(
                id,
                "FILE",
                42,
                userId,
                readable,
                writable,
                manageable,
                status,
                new Date(),
                new Date(System.currentTimeMillis() + 86_400_000L));
    }
}
