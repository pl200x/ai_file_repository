package com.example.file_management.controller.dto;

public record InvitationDTO(int requestUserId, int targetUserId,
                            String targetType, int targetId, String permissionType,
                            boolean needConfirmation,
                            long expirationDate) {

}
