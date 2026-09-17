package com.example.file_management.enums;

import lombok.Getter;

@Getter
public enum NotificationTopic {
    APPLY_PERMISSION("APPLY_PERMISSION","Apply access to a new file/repository"),
    LIKE("LIKE","like a new file"),
    COMMENT("COMMENT","comment to a new file");

    private String code;
    private String description;

    NotificationTopic(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
