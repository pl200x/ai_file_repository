package com.example.permission.service.impl;

import com.example.permission.Exception.PermissionNotFoundException;
import com.example.permission.Exception.PermissionTypeUnkownException;
import com.example.permission.controller.dto.PermissionDTO;
import com.example.permission.entity.Permission;
import com.example.permission.enums.PermissionStatus;
import com.example.permission.enums.PermissionTargetType;
import com.example.permission.mapper.PermissionMapper;
import com.example.permission.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper PermissionMapper;

    @Autowired
    public PermissionServiceImpl(PermissionMapper PermissionMapper) {
        this.PermissionMapper = PermissionMapper;
    }

    @Override
    public Permission givePermission(int userId, int targetId, String type, String permission, long expirationTime) {
        String targetType = normalizeTargetType(type);

        Permission Permission = selectByTargetAndUser(targetType, targetId, userId);
        boolean isNew = Permission == null;

        if (isNew) {
            Permission = new Permission();
            Permission.setType(targetType);
            Permission.setTargetId(targetId);
            Permission.setUserId(userId);
        }

        applyPermissionLevel(Permission, permission);

        Permission.setStatus(PermissionStatus.PENDING.getCode());
        Permission.setExpirationTime(new Date(System.currentTimeMillis() + expirationTime));

        if (isNew) {
            PermissionMapper.insert(Permission);
        } else {
            PermissionMapper.updateById(Permission);
        }

        return Permission;
    }

    @Override
    public List<Permission> batchGivePermission(List<PermissionDTO> permissionDTOList) {
        List<Permission> PermissionList = new ArrayList<>();
        if (permissionDTOList == null || permissionDTOList.isEmpty()) {
            return PermissionList;
        }

        for (PermissionDTO permissionDTO : permissionDTOList) {
            Permission Permission = new Permission();
            Permission.setType(normalizeTargetType(permissionDTO.getType()));
            Permission.setTargetId(permissionDTO.getTargetId());
            Permission.setUserId(permissionDTO.getUserId());

            applyPermissionLevel(Permission, permissionDTO.getPermission());

            Permission.setStatus(PermissionStatus.APPROVED.getCode());
            Permission.setExpirationTime(new Date(System.currentTimeMillis() + permissionDTO.getExpirationTime()));

            PermissionList.add(Permission);
        }

        PermissionMapper.batchInsert(PermissionList);
        return PermissionList;
    }

    @Override
    public Permission approvePermission(String type, int targetId, int userId) {
        Permission Permission = requireExisting(type, targetId, userId);

        Permission.setStatus(PermissionStatus.APPROVED.getCode());

        PermissionMapper.updateById(Permission);
        return Permission;
    }

    @Override
    public Permission rejectPermission(String type, int targetId, int userId) {
        Permission Permission = requireExisting(type, targetId, userId);

        Permission.setStatus(PermissionStatus.REJECTED.getCode());

        PermissionMapper.updateById(Permission);
        return Permission;
    }

    @Override
    public Permission revokePermission(String type, int targetId, int userId) {
        Permission Permission = requireExisting(type, targetId, userId);

        Permission.setStatus(PermissionStatus.REVOKED.getCode());
        Permission.setReadable(false);
        Permission.setWritable(false);
        Permission.setManageable(false);

        PermissionMapper.updateById(Permission);
        return Permission;
    }

    @Override
    public void deletePermissionsByTarget(String type, int targetId) {
        PermissionMapper.deleteByTypeAndTargetId(normalizeTargetType(type), targetId);
    }

    @Override
    public Permission getPermission(String type, int targetId, int userId) {
        return requireExisting(type, targetId, userId);
    }

    @Override
    public List<Permission> getPermissionsByTarget(String type, int targetId) {
        return PermissionMapper.selectByType(normalizeTargetType(type), targetId);
    }

    @Override
    public List<Permission> getPermissionsByUserId(int userId) {
        return PermissionMapper.selectByUserId(userId);
    }

    @Override
    public List<Permission> getValidPermissionsByUserId(int userId) {
        return PermissionMapper.selectValidPermissionsByUserId(userId, new Date());
    }

    private void applyPermissionLevel(Permission Permission, String permission) {
        switch (permission.toLowerCase()) {
            case "readable":
                Permission.setReadable(true);
                break;
            case "writable":
                Permission.setReadable(true);
                Permission.setWritable(true);
                break;
            case "manageable":
                Permission.setReadable(true);
                Permission.setWritable(true);
                Permission.setManageable(true);
                break;
            default:
                throw new PermissionTypeUnkownException("enter correct permission type");
        }
    }

    private Permission requireExisting(String type, int targetId, int userId) {
        Permission Permission = selectByTargetAndUser(normalizeTargetType(type), targetId, userId);
        if (Permission == null) {
            throw new PermissionNotFoundException(
                    "No permission request found for type=" + type + ", targetId=" + targetId + ", userId=" + userId);
        }
        return Permission;
    }

    private Permission selectByTargetAndUser(String type, int targetId, int userId) {
        return PermissionMapper.selectByTypeAndTargetIdAndUserId(type, targetId, userId);
    }

    private String normalizeTargetType(String type) {
        if (type != null) {
            for (PermissionTargetType targetType : PermissionTargetType.values()) {
                if (targetType.getCode().equalsIgnoreCase(type)) {
                    return targetType.getCode();
                }
            }
        }
        throw new PermissionTypeUnkownException(
                "unknown target type: " + type + ", expected FILE or KNOWLEDGE_REPOSITORY");
    }
}
