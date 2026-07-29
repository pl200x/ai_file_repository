package com.example.file_management.service;

import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.FileVersion;

import java.util.List;

public interface FileVersionService {

    /**
     * 版本列表（不含content，只有元信息），version_no 倒序。文档已创建后走这里，按file_id查，不受改名影响。
     */
    List<FileVersion> queryVersionsByFileId(int fileId, int userId);

    /**
     * 文档尚未创建时的版本历史（新建草稿阶段）：靠defaultTitle定位，不受草稿期改标题影响。
     */
    List<FileVersion> queryVersionsByDefaultTitle(
            String defaultTitle,
            int repositoryId,
            int tenantId,
            int userId);

    /**
     * 单个版本详情（含content快照）。
     */
    FileVersion queryVersionDetail(int fileId, int versionNo, int userId);

    /**
     * Idempotently creates the initial draft version for a defaultTitle chain.
     */
    FileWriteResultVO initializeFileVersion(FileVersionDTO fileVersionDTO);

    /**
     * Appends an immutable snapshot. A positive incoming versionNo is treated as
     * the caller's expected latest version and protects against stale writes.
     */
    FileWriteResultVO addFileVersion(FileVersionDTO fileVersionDTO);
}
