package com.example.file_management.integration;

import com.example.file_management.controller.vo.BaseVO;
import com.example.file_management.integration.vo.SinglePermissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionIntegrationTest {

    @Mock
    private RestTemplate restTemplate;

    private PermissionIntegration permissionIntegration;

    @BeforeEach
    void setUp() {
        permissionIntegration = new PermissionIntegration();
        ReflectionTestUtils.setField(
                permissionIntegration, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(
                permissionIntegration,
                "permissionBaseUrl",
                "http://permission-service");
    }

    @Test
    void checkPermission_business404MeansNoPermission() {
        SinglePermissionVO response = new SinglePermissionVO();
        response.setBaseVO(
                BaseVO.buildVO(404, 1L, false, "permission not found"));
        when(restTemplate.getForEntity(
                anyString(),
                eq(SinglePermissionVO.class),
                any(Object[].class)))
                .thenReturn(ResponseEntity.ok(response));

        boolean result =
                permissionIntegration.checkPermissionByTypeTargetUserId(
                        "FILE", 42, 9, "READABLE");

        assertFalse(result);
    }

    @Test
    void approvePermission_sendsActionDtoAsJsonBody() {
        when(restTemplate.postForEntity(
                eq("http://permission-service/api/permissions/approve"),
                any(HttpEntity.class),
                eq(BaseVO.class)))
                .thenReturn(ResponseEntity.ok(
                        BaseVO.buildVO(200, 1L, true, null)));

        permissionIntegration.approvePermission("FILE", 42, 9);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://permission-service/api/permissions/approve"),
                entityCaptor.capture(),
                eq(BaseVO.class));
        PermissionActionDTO action =
                (PermissionActionDTO) entityCaptor.getValue().getBody();
        assertEquals("FILE", action.type());
        assertEquals(42, action.targetId());
        assertEquals(9, action.userId());
    }
}
