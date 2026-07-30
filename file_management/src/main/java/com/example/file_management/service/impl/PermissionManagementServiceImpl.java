package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.InvitationDTO;
import com.example.file_management.controller.dto.PermissionOperationDTO;
import com.example.file_management.controller.dto.RequestPermissionDTO;
import com.example.file_management.controller.vo.UserPermissionVO;
import com.example.file_management.entity.File;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.entity.User;
import com.example.file_management.enums.PermissionTargetType;
import com.example.file_management.enums.PermissionType;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.UserNotExistException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.integration.PermissionDTO;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.integration.vo.PermissionVO;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.service.PermissionManagementService;
import com.example.file_management.service.KnowledgeRepositoryService;
import com.example.file_management.service.UserService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PermissionManagementServiceImpl implements PermissionManagementService {
    private static final long ONE_DAY_MS = 86_400_000L;
    private static final long THREE_DAYS_MS = 259_200_000L;
    private static final long ONE_MONTH_MS = 2_592_000_000L;
    private static final long ONE_YEAR_MS = 31_536_000_000L;
    private static final Set<Long> ALLOWED_EXPIRATION_DURATIONS = Set.of(
            ONE_DAY_MS,
            THREE_DAYS_MS,
            ONE_MONTH_MS,
            ONE_YEAR_MS);

    private final PermissionIntegration permissionIntegration;
    private final UserService userService;
    private final KnowledgeRepositoryService knowledgeRepositoryService;
    private final FileMapper fileMapper;

    public PermissionManagementServiceImpl(
            PermissionIntegration permissionIntegration,
            UserService userService,
            KnowledgeRepositoryService knowledgeRepositoryService,
            FileMapper fileMapper) {
        this.permissionIntegration = permissionIntegration;
        this.userService = userService;
        this.knowledgeRepositoryService = knowledgeRepositoryService;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<UserPermissionVO> queryPermissionsByTarget(
            int requestUserId,
            String targetType,
            int targetId) {
        String normalizedTargetType =
                validateTargetAndActor(requestUserId, targetType, targetId);
        requireManageable(requestUserId, normalizedTargetType, targetId);

        List<PermissionVO> permissions =
                permissionIntegration.getPermissionsByTarget(
                        normalizedTargetType, targetId);
        List<Integer> userIds = permissions.stream()
                .map(PermissionVO::getUserId)
                .distinct()
                .toList();
        Map<Integer, User> userById = new LinkedHashMap<>();
        for (User user : userService.queryByIds(userIds)) {
            userById.put(user.getId(), user);
        }

        return permissions.stream()
                .map(permission -> toUserPermissionVO(
                        permission, userById.get(permission.getUserId())))
                .toList();
    }

    @Override
    public void inviteUser(InvitationDTO invitationDTO) {
        requireInvitation(invitationDTO);
        String targetType = validateTargetAndActor(
                invitationDTO.requestUserId(),
                invitationDTO.targetType(),
                invitationDTO.targetId());
        requireSameTenantUsers(
                invitationDTO.requestUserId(),
                invitationDTO.targetUserId());
        requireManageable(
                invitationDTO.requestUserId(),
                targetType,
                invitationDTO.targetId());

        String permissionType =
                normalizePermissionType(invitationDTO.permissionType());
        if (permissionIntegration.checkPermissionByTypeTargetUserId(
                targetType,
                invitationDTO.targetId(),
                invitationDTO.targetUserId(),
                permissionType)) {
            return;
        }

        permissionIntegration.givePermissionByUserID(buildPermissionDTO(
                invitationDTO.targetUserId(),
                invitationDTO.targetId(),
                targetType,
                permissionType,
                invitationDTO.expirationDate()));
        if (!invitationDTO.needConfirmation()) {
            permissionIntegration.approvePermission(
                    targetType,
                    invitationDTO.targetId(),
                    invitationDTO.targetUserId());
        }
    }

    @Override
    public void requestPermission(RequestPermissionDTO requestPermissionDTO) {
        requirePermissionRequest(requestPermissionDTO);
        String targetType = validateTargetAndActor(
                requestPermissionDTO.requestUserId(),
                requestPermissionDTO.targetType(),
                requestPermissionDTO.targetId());
        requireSameTenantUsers(
                requestPermissionDTO.requestUserId(),
                requestPermissionDTO.targetUserId());
        if (requestPermissionDTO.requestUserId()
                != requestPermissionDTO.targetUserId()) {
            requireManageable(
                    requestPermissionDTO.requestUserId(),
                    targetType,
                    requestPermissionDTO.targetId());
        }

        String permissionType =
                normalizePermissionType(requestPermissionDTO.permissionType());
        if (permissionIntegration.checkPermissionByTypeTargetUserId(
                targetType,
                requestPermissionDTO.targetId(),
                requestPermissionDTO.targetUserId(),
                permissionType)) {
            return;
        }

        permissionIntegration.givePermissionByUserID(buildPermissionDTO(
                requestPermissionDTO.targetUserId(),
                requestPermissionDTO.targetId(),
                targetType,
                permissionType,
                requestPermissionDTO.expirationDate()));
    }

    @Override
    public void approvePermission(PermissionOperationDTO permissionOperationDTO) {
        PermissionOperationDTO operation = requireOperation(permissionOperationDTO);
        String targetType = validateOperation(operation);
        permissionIntegration.approvePermission(
                targetType, operation.targetId(), operation.targetUserId());
    }

    @Override
    public void rejectPermission(PermissionOperationDTO permissionOperationDTO) {
        PermissionOperationDTO operation = requireOperation(permissionOperationDTO);
        String targetType = validateOperation(operation);
        permissionIntegration.rejectPermission(
                targetType, operation.targetId(), operation.targetUserId());
    }

    @Override
    public void revokePermission(PermissionOperationDTO permissionOperationDTO) {
        PermissionOperationDTO operation = requireOperation(permissionOperationDTO);
        String targetType = validateOperation(operation);
        permissionIntegration.revokePermission(
                targetType, operation.targetId(), operation.targetUserId());
    }

    private String validateOperation(PermissionOperationDTO operation) {
        String targetType = validateTargetAndActor(
                operation.requestUserId(),
                operation.targetType(),
                operation.targetId());
        requireUser(operation.targetUserId());
        requireManageable(
                operation.requestUserId(), targetType, operation.targetId());
        return targetType;
    }

    private String validateTargetAndActor(
            int requestUserId,
            String targetType,
            int targetId) {
        requirePositive(requestUserId, "requestUserId");
        requirePositive(targetId, "targetId");
        User requestUser = requireUser(requestUserId);
        String normalizedTargetType = normalizeTargetType(targetType);

        int targetTenantId;
        if (PermissionTargetType.KNOWLEDGE_REPOSITORY.getCode()
                .equals(normalizedTargetType)) {
            KnowledgeRepository repository =
                    knowledgeRepositoryService.queryById(targetId);
            if (repository == null) {
                throw new CantFindTargetFileException(
                        "The target repository does not exist");
            }
            targetTenantId = repository.getTenantId();
        } else {
            File file = fileMapper.queryById(targetId);
            if (file == null) {
                throw new CantFindTargetFileException(
                        "The target file does not exist");
            }
            targetTenantId = file.getTenantId();
        }

        if (requestUser.getTenantId() != targetTenantId) {
            throw new UserPermissionDeniedException(
                    "The requesting user and target belong to different tenants");
        }
        return normalizedTargetType;
    }

    private void requireManageable(
            int requestUserId,
            String targetType,
            int targetId) {
        boolean manageable =
                permissionIntegration.checkPermissionByTypeTargetUserId(
                        targetType,
                        targetId,
                        requestUserId,
                        PermissionType.MANAGEABLE.getCode());
        if (!manageable) {
            throw new UserPermissionDeniedException(
                    "You don't have permission to manage this target");
        }
    }

    private PermissionDTO buildPermissionDTO(
            int userId,
            int targetId,
            String targetType,
            String permissionType,
            long expirationDurationMs) {
        validateExpirationDuration(expirationDurationMs);
        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setUserId(userId);
        permissionDTO.setTargetId(targetId);
        permissionDTO.setType(targetType);
        permissionDTO.setPermission(permissionType);
        permissionDTO.setExpirationTime(expirationDurationMs);
        return permissionDTO;
    }

    private UserPermissionVO toUserPermissionVO(
            PermissionVO permission,
            User user) {
        if (user == null) {
            throw new UserNotExistException(
                    "Permission references unknown user "
                            + permission.getUserId());
        }
        UserPermissionVO vo = new UserPermissionVO();
        vo.setPermissionId(permission.getId());
        vo.setUserId(permission.getUserId());
        vo.setTargetType(permission.getType());
        vo.setTargetId(permission.getTargetId());
        vo.setProfile(user.getProfile());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setPermissionType(highestPermissionType(permission));
        vo.setStatus(permission.getStatus());
        vo.setExpirationTime(permission.getExpirationTime());
        return vo;
    }

    private String highestPermissionType(PermissionVO permission) {
        if (Boolean.TRUE.equals(permission.getManageable())) {
            return PermissionType.MANAGEABLE.getCode();
        }
        if (Boolean.TRUE.equals(permission.getWritable())) {
            return PermissionType.WRITABLE.getCode();
        }
        if (Boolean.TRUE.equals(permission.getReadable())) {
            return PermissionType.READABLE.getCode();
        }
        return "NONE";
    }

    private User requireUser(int userId) {
        requirePositive(userId, "userId");
        User user = userService.queryById(userId);
        if (user == null) {
            throw new UserNotExistException(
                    "The target user does not exist");
        }
        return user;
    }

    private void requireSameTenantUsers(
            int requestUserId,
            int targetUserId) {
        User requestUser = requireUser(requestUserId);
        User targetUser = requireUser(targetUserId);
        if (requestUser.getTenantId() != targetUser.getTenantId()) {
            throw new UserPermissionDeniedException(
                    "The requesting user and invited user belong to different tenants");
        }
    }

    private String normalizeTargetType(String targetType) {
        if (targetType != null) {
            for (PermissionTargetType type : PermissionTargetType.values()) {
                if (type.getCode().equalsIgnoreCase(targetType.trim())) {
                    return type.getCode();
                }
            }
        }
        throw new IllegalArgumentException(
                "targetType must be FILE or KNOWLEDGE_REPOSITORY");
    }

    private String normalizePermissionType(String permissionType) {
        if (permissionType != null) {
            for (PermissionType type : PermissionType.values()) {
                if (type.getCode().equalsIgnoreCase(permissionType.trim())) {
                    return type.getCode();
                }
            }
        }
        throw new IllegalArgumentException(
                "permissionType must be READABLE, WRITABLE or MANAGEABLE");
    }

    private void validateExpirationDuration(long expirationDurationMs) {
        if (!ALLOWED_EXPIRATION_DURATIONS.contains(expirationDurationMs)) {
            throw new IllegalArgumentException(
                    "expirationDate must be one of the supported durations");
        }
    }

    private void requireInvitation(InvitationDTO invitationDTO) {
        if (invitationDTO == null) {
            throw new IllegalArgumentException("invitation is required");
        }
        requirePositive(invitationDTO.targetUserId(), "targetUserId");
        validateExpirationDuration(invitationDTO.expirationDate());
    }

    private void requirePermissionRequest(
            RequestPermissionDTO requestPermissionDTO) {
        if (requestPermissionDTO == null) {
            throw new IllegalArgumentException(
                    "permission request is required");
        }
        requirePositive(requestPermissionDTO.targetUserId(), "targetUserId");
        validateExpirationDuration(requestPermissionDTO.expirationDate());
    }

    private PermissionOperationDTO requireOperation(
            PermissionOperationDTO permissionOperationDTO) {
        if (permissionOperationDTO == null) {
            throw new IllegalArgumentException(
                    "permission operation is required");
        }
        requirePositive(
                permissionOperationDTO.targetUserId(), "targetUserId");
        return permissionOperationDTO;
    }

    private void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
