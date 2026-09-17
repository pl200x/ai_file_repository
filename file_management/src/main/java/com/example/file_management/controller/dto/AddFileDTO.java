package com.example.file_management.controller.dto;

import java.util.Date;

public record AddFileDTO(
        int repositoryId,
        int ownerId,
        String title,
        String content,
        //null/blank会在service层按File.CONTENT_FORMAT_PLAIN处理；
        //手动创建/更新文档的JSON请求体不需要传这个字段
        String contentFormat,
        String writableList,
        String readableList,
        String manageableList,
        Date recentUpdateTime,
        int latestModifiedUserId,
        int tenantId,
        boolean isPrivate,
        //新建文档草稿阶段由前端生成的idempotency key，提交时带过来，
        //让首个FileVersion(v1)接上草稿阶段已经存在的版本链
        String defaultTitle,
        //true means the user left the title empty and the backend must derive a collision-free title
        boolean autoTitle
) {
}
