package com.example.file_management.agent;

/** A retrieval hit whose file is readable by the current user. */
public record AuthorizedChunk(
        int fileId,
        String fileName,
        int chunkIndex,
        String content) {
}
