package com.example.file_management.enums;

import lombok.Getter;

@Getter
public enum NotificationTargetType {
    KNOWLEDGE_REPOSITORY("KNOWLEDGE_REPOSITORY","知识库"),
    FILE("FILE","文档"),
    COMMENT("COMMENT","评论");

    private String code;
    private String description;

    NotificationTargetType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
