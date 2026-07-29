package com.example.file_management.controller.dto;

public record UpdateFileDTO(
        int id,
        String title,
        String content,
        String writableList,
        String readableList,
        String manageableList,
        int latestModifiedUserId,
        boolean isPrivate,
        //文档已存在，defaultTitle是它建档时就固定下来的idempotency key，
        //由前端从该文件的版本历史里读出后原样回传，backend不再自动推导
        String defaultTitle,
        //true means this submission still uses a title derived from document content
        boolean autoTitle
) {
}
