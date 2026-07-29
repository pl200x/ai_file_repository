package com.example.file_management.controller.dto;

public record AddRepositoryDTO(
        int tenantId,
        int ownerId,
        String title,
        String description,
        String writableList,
        String readableList,
        String manageableList,
        boolean isPersonal
) {
}
