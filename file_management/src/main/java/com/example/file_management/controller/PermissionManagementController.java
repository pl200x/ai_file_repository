package com.example.file_management.controller;

import com.example.file_management.controller.dto.InvitationDTO;
import com.example.file_management.controller.dto.PermissionOperationDTO;
import com.example.file_management.controller.dto.RequestPermissionDTO;
import com.example.file_management.controller.vo.UserPermissionVO;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.UserNotExistException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.service.PermissionManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permission_management")
public class PermissionManagementController {
    private static final Logger logger =
            LoggerFactory.getLogger(PermissionManagementController.class);

    private final PermissionManagementService permissionManagementService;

    public PermissionManagementController(
            PermissionManagementService permissionManagementService) {
        this.permissionManagementService = permissionManagementService;
    }

    @GetMapping("/get_repository_permission_list")
    public DataVO<List<UserPermissionVO>> requestAllRepositoryPermissions(
            @RequestParam int requestUserId,
            @RequestParam String targetType,
            @RequestParam int targetId) {
        long start = System.currentTimeMillis();
        try {
            List<UserPermissionVO> permissions =
                    permissionManagementService.queryPermissionsByTarget(
                            requestUserId, targetType, targetId);
            return success(start, permissions);
        } catch (Exception exception) {
            return failure(start, exception, "query target permissions");
        }
    }

    @PostMapping("/invite_user")
    public DataVO<Void> inviteUser(
            @RequestBody InvitationDTO invitationDTO) {
        long start = System.currentTimeMillis();
        try {
            permissionManagementService.inviteUser(invitationDTO);
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "invite user");
        }
    }

    @PostMapping("/request_permission")
    public DataVO<Void> requestPermission(
            @RequestBody RequestPermissionDTO requestPermissionDTO) {
        long start = System.currentTimeMillis();
        try {
            permissionManagementService.requestPermission(requestPermissionDTO);
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "request permission");
        }
    }

    @PutMapping("/approve_permission")
    public DataVO<Void> approvePermission(
            @RequestBody PermissionOperationDTO permissionOperationDTO) {
        long start = System.currentTimeMillis();
        try {
            permissionManagementService.approvePermission(
                    permissionOperationDTO);
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "approve permission");
        }
    }

    @PutMapping("/reject_permission")
    public DataVO<Void> rejectPermission(
            @RequestBody PermissionOperationDTO permissionOperationDTO) {
        long start = System.currentTimeMillis();
        try {
            permissionManagementService.rejectPermission(
                    permissionOperationDTO);
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "reject permission");
        }
    }

    @PutMapping("/revoke_permission")
    public DataVO<Void> revokePermission(
            @RequestBody PermissionOperationDTO permissionOperationDTO) {
        long start = System.currentTimeMillis();
        try {
            permissionManagementService.revokePermission(
                    permissionOperationDTO);
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "revoke permission");
        }
    }

    private <T> DataVO<T> success(long start, T data) {
        return DataVO.buildDataVO(
                200,
                System.currentTimeMillis() - start,
                true,
                null,
                data);
    }

    private <T> DataVO<T> failure(
            long start,
            Exception exception,
            String operation) {
        long elapsed = System.currentTimeMillis() - start;
        if (exception instanceof IllegalArgumentException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    400, elapsed, false, exception.getMessage(), null);
        }
        if (exception instanceof CantFindTargetFileException
                || exception instanceof UserNotExistException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    404, elapsed, false, exception.getMessage(), null);
        }
        if (exception instanceof UserPermissionDeniedException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    501, elapsed, false, exception.getMessage(), null);
        }
        logger.error("failed to " + operation, exception);
        return DataVO.buildDataVO(
                500, elapsed, false, "other unknown error", null);
    }
}
