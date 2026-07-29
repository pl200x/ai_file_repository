package com.example.file_management.controller.dto;

import java.util.Date;

public record FileVersionDTO(Long id,Integer fileId, int versionNo, String title,
        String content, int editorId, Date openTime, Date lastMergeTime, int repositoryId, int tenantId,
        //idempotency key：文档创建前由前端在首次点击"添加文档"时生成并固定下来，
        //文档创建后由service沿用链上已有版本的值，前端不必再传
        String defaultTitle
       ) {
}
