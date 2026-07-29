package com.example.file_management.controller;

import com.example.file_management.controller.dto.InvitationDTO;
import com.example.file_management.controller.dto.PermissionOperationDTO;
import com.example.file_management.controller.dto.RequestPermissionDTO;
import com.example.file_management.controller.vo.UserPermissionVO;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.service.PermissionManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PermissionManagementController.class)
class PermissionManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PermissionManagementService permissionManagementService;

    @Test
    void queryPermissions_passesActorAndTargetToService() throws Exception {
        UserPermissionVO permission = new UserPermissionVO();
        permission.setPermissionId(7);
        permission.setUserId(9);
        permission.setName("Ada");
        permission.setPermissionType("WRITABLE");
        when(permissionManagementService.queryPermissionsByTarget(
                1, "KNOWLEDGE_REPOSITORY", 42))
                .thenReturn(List.of(permission));

        mockMvc.perform(get(
                        "/api/permission_management/get_repository_permission_list")
                        .param("requestUserId", "1")
                        .param("targetType", "KNOWLEDGE_REPOSITORY")
                        .param("targetId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].permissionId").value(7))
                .andExpect(jsonPath("$.data[0].name").value("Ada"))
                .andExpect(jsonPath("$.data[0].permissionType")
                        .value("WRITABLE"));
    }

    @Test
    void inviteUser_deserializesDtoAndDelegates() throws Exception {
        mockMvc.perform(post("/api/permission_management/invite_user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestUserId": 1,
                                  "targetUserId": 9,
                                  "targetType": "FILE",
                                  "targetId": 42,
                                  "permissionType": "READABLE",
                                  "needConfirmation": true,
                                  "expirationDate": 86400000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<InvitationDTO> captor =
                ArgumentCaptor.forClass(InvitationDTO.class);
        verify(permissionManagementService).inviteUser(captor.capture());
        assertEquals(1, captor.getValue().requestUserId());
        assertEquals(9, captor.getValue().targetUserId());
        assertEquals(86_400_000L, captor.getValue().expirationDate());
    }

    @Test
    void requestPermission_deserializesDurationAsLong() throws Exception {
        mockMvc.perform(post("/api/permission_management/request_permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestUserId": 9,
                                  "targetUserId": 9,
                                  "targetType": "FILE",
                                  "targetId": 42,
                                  "permissionType": "WRITABLE",
                                  "expirationDate": 31536000000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<RequestPermissionDTO> captor =
                ArgumentCaptor.forClass(RequestPermissionDTO.class);
        verify(permissionManagementService)
                .requestPermission(captor.capture());
        assertEquals(31_536_000_000L, captor.getValue().expirationDate());
    }

    @Test
    void permissionActions_deserializeOperationDto() throws Exception {
        String body = """
                {
                  "requestUserId": 1,
                  "targetUserId": 9,
                  "targetType": "FILE",
                  "targetId": 42
                }
                """;

        mockMvc.perform(put("/api/permission_management/approve_permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(put("/api/permission_management/reject_permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(put("/api/permission_management/revoke_permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        PermissionOperationDTO operation =
                new PermissionOperationDTO(1, 9, "FILE", 42);
        verify(permissionManagementService).approvePermission(operation);
        verify(permissionManagementService).rejectPermission(operation);
        verify(permissionManagementService).revokePermission(operation);
    }

    @Test
    void permissionDenied_returnsProjectBusinessCode501() throws Exception {
        doThrow(new UserPermissionDeniedException("not manageable"))
                .when(permissionManagementService)
                .approvePermission(any(PermissionOperationDTO.class));

        mockMvc.perform(put("/api/permission_management/approve_permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestUserId": 1,
                                  "targetUserId": 9,
                                  "targetType": "FILE",
                                  "targetId": 42
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage")
                        .value("not manageable"));
    }
}
