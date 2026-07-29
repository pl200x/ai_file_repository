package com.example.permission.service.impl;

import com.example.permission.Exception.PermissionNotFoundException;
import com.example.permission.mapper.PermissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplTest {

    @Mock
    private PermissionMapper permissionMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Test
    void getPermission_whenRecordDoesNotExist_throwsNotFoundException() {
        when(permissionMapper.selectByTypeAndTargetIdAndUserId(
                "FILE", 42, 9)).thenReturn(null);

        assertThrows(
                PermissionNotFoundException.class,
                () -> permissionService.getPermission("FILE", 42, 9));
    }
}
