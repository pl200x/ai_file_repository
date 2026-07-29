package com.example.file_management.controller.dto;

public record RequestPermissionDTO(int requestUserId, int targetUserId,
                                   String targetType, int targetId, String permissionType,
                                   long expirationDate) {
}
