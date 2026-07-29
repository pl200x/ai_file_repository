package com.example.file_management.service;

import com.example.file_management.controller.dto.InvitationDTO;
import com.example.file_management.controller.dto.PermissionOperationDTO;
import com.example.file_management.controller.dto.RequestPermissionDTO;
import com.example.file_management.controller.vo.UserPermissionVO;

import java.util.List;

public interface PermissionManagementService {
    List<UserPermissionVO> queryPermissionsByTarget(
            int requestUserId,
            String targetType,
            int targetId);

    void inviteUser(InvitationDTO invitationDTO);

    void requestPermission(RequestPermissionDTO requestPermissionDTO);

    void approvePermission(PermissionOperationDTO permissionOperationDTO);

    void rejectPermission(PermissionOperationDTO permissionOperationDTO);

    void revokePermission(PermissionOperationDTO permissionOperationDTO);
}
