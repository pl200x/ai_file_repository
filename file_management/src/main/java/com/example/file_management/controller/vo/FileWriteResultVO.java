package com.example.file_management.controller.vo;

/**
 * The stable identity returned by every document write operation.
 *
 * @param fileId the materialized file id, or {@code null} while the document is still a draft
 * @param versionNo the latest version number in this document chain
 * @param defaultTitle the stable key of the document/version chain
 * @param title the effective display title stored in the snapshot/file
 */
public record FileWriteResultVO(
        Integer fileId,
        int versionNo,
        String defaultTitle,
        String title
) {
}
