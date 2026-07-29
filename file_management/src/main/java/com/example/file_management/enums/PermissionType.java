package com.example.file_management.enums;

public enum PermissionType {

    READABLE("READABLE", "可读"),
    WRITABLE("WRITABLE", "可写"),
    MANAGEABLE("MANAGEABLE", "可管理");

    private final String code;
    private final String description;

    PermissionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

//    public static PermissionStatus fromCode(String code) {
//        if (code == null || code.isBlank()) {
//            throw new IllegalArgumentException("Permission action code cannot be null or blank");
//        }
//
//        for (PermissionType action : values()) {
//            if (action.code.equalsIgnoreCase(code.trim())) {
//                return action;
//            }
//        }
//
//        throw new IllegalArgumentException("Unknown permission action code: " + code);
//    }
}