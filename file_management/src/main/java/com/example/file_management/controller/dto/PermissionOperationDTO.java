package com.example.file_management.controller.dto;

public record PermissionOperationDTO(
        int requestUserId,
        int targetUserId,
        String targetType,
        int targetId) {
}
