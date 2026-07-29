package com.example.permission.enums;

import lombok.Getter;

@Getter
public enum PermissionTargetType {
    KNOWLEDGE_REPOSITORY("KNOWLEDGE_REPOSITORY","知识库"),
    FILE("FILE","文档");

    private String code;
    private String description;

    PermissionTargetType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
