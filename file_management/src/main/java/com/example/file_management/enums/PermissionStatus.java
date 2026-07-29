package com.example.file_management.enums;

public enum PermissionStatus {

    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已批准"),
    REJECTED("REJECTED", "已拒绝"),
    REVOKED("REVOKED", "已撤销"),
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String description;

    PermissionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static PermissionStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Permission status code cannot be null or blank");
        }

        for (PermissionStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown permission status code: " + code);
    }
}
