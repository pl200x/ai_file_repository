package com.example.permission.service;

import com.example.permission.controller.dto.PermissionDTO;
import com.example.permission.entity.Permission;

import java.util.List;

public interface PermissionService {

    /**
     * Request a permission grant for a user on a target (a file or a whole knowledge
     * repository, distinguished by type). The record is created (or updated, if one
     * already exists for this target/user) in PENDING status and must be approved
     * before it takes effect.
     *
     * @param type           target type, a PermissionTargetType code (FILE / KNOWLEDGE_REPOSITORY)
     * @param permission     requested level: readable / writable / manageable
     * @param expirationTime validity duration in milliseconds from now
     */
    Permission givePermission(int userId, int targetId, String type, String permission, long expirationTime);

    /**
     * Batch version of {@link #givePermission}: grant permissions for several
     * user/target pairs in one call. Unlike the single give, records are created
     * directly in APPROVED status (no approval step) through a single multi-row
     * INSERT, so the batch is all-or-nothing — if any item has an unknown
     * type/permission level, or collides with an existing (type, targetId, userId)
     * record, nothing is inserted.
     */
    List<Permission> batchGivePermission(List<PermissionDTO> permissionDTOList);

    /**
     * Approve a pending permission request.
     */
    Permission approvePermission(String type, int targetId, int userId);

    Permission rejectPermission(String type, int targetId, int userId);

    /**
     * Revoke a previously granted permission, clearing readable/writable/manageable.
     */
    Permission revokePermission(String type, int targetId, int userId);

    /**
     * Physically delete every permission record of one target, regardless of user
     * or status. Called when the target itself (e.g. a file) is deleted.
     */
    void deletePermissionsByTarget(String type, int targetId);

    Permission getPermission(String type, int targetId, int userId);

    List<Permission> getPermissionsByTarget(String type, int targetId);

    List<Permission> getPermissionsByUserId(int userId);

    /**
     * Approved permissions that are currently within their validity window.
     */
    List<Permission> getValidPermissionsByUserId(int userId);
}