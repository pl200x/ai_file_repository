package com.example.file_management.integration;

public record PermissionActionDTO(
        String type,
        int targetId,
        int userId) {
}
