package com.example.file_management.controller.dto;

public record AddUserDTO(
        int tenantId,
        int groupId,
        String name,
        String email,
        String profile
) {
}
