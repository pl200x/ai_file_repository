package com.example.file_management.controller;

import com.example.file_management.controller.dto.AddUserDTO;
import com.example.file_management.entity.User;
import com.example.file_management.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void listUsers_returnsTenantUsers() throws Exception {
        User user = new User();
        user.setId(6);
        user.setTenantId(1);
        user.setGroupId(1);
        user.setName("user_test");
        user.setEmail("user6@example.com");
        when(userService.queryByTenantId(1)).thenReturn(List.of(user));

        mockMvc.perform(get("/api/user/list").param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(6))
                .andExpect(jsonPath("$.data[0].tenantId").value(1))
                .andExpect(jsonPath("$.data[0].groupId").value(1))
                .andExpect(jsonPath("$.data[0].name").value("user_test"));
    }

    @Test
    void queryUser_returnsBusiness404WhenMissing() throws Exception {
        when(userService.queryById(99)).thenReturn(null);

        mockMvc.perform(get("/api/user/query").param("id", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage")
                        .value("user does not exist"));
    }

    @Test
    void addUser_deserializesAndDelegates() throws Exception {
        mockMvc.perform(post("/api/user/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": 1,
                                  "groupId": 1,
                                  "name": "user_test",
                                  "email": "user6@example.com",
                                  "profile": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<AddUserDTO> captor =
                ArgumentCaptor.forClass(AddUserDTO.class);
        verify(userService).addUser(captor.capture());
        assertEquals(1, captor.getValue().tenantId());
        assertEquals(1, captor.getValue().groupId());
        assertEquals("user_test", captor.getValue().name());
        assertEquals("user6@example.com", captor.getValue().email());
    }

    @Test
    void listUsers_rejectsInvalidTenant() throws Exception {
        mockMvc.perform(get("/api/user/list").param("tenantId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage")
                        .value("tenantId must be positive"));
    }
}
