package com.example.permission.controller;

import com.example.permission.Exception.PermissionNotFoundException;
import com.example.permission.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PermissionController.class)
class PermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PermissionService permissionService;

    @Test
    void approvePermission_acceptsActionDto() throws Exception {
        mockMvc.perform(post("/api/permissions/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        verify(permissionService)
                .approvePermission("FILE", 42, 9);
    }

    @Test
    void rejectPermission_acceptsActionDto() throws Exception {
        mockMvc.perform(post("/api/permissions/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        verify(permissionService)
                .rejectPermission("FILE", 42, 9);
    }

    @Test
    void revokePermission_acceptsActionDto() throws Exception {
        mockMvc.perform(post("/api/permissions/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        verify(permissionService)
                .revokePermission("FILE", 42, 9);
    }

    @Test
    void getPermission_whenMissing_returnsBusiness404InsteadOf500()
            throws Exception {
        doThrow(new PermissionNotFoundException("permission not found"))
                .when(permissionService)
                .getPermission("FILE", 42, 9);

        mockMvc.perform(get("/api/permissions/FILE/42/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseVO.code").value(404))
                .andExpect(jsonPath("$.baseVO.success").value(false))
                .andExpect(jsonPath("$.baseVO.errorMessage")
                        .value("permission not found"))
                .andExpect(jsonPath("$.permissionVO").doesNotExist());
    }

    private String actionBody() {
        return """
                {
                  "type": "FILE",
                  "targetId": 42,
                  "userId": 9
                }
                """;
    }
}
