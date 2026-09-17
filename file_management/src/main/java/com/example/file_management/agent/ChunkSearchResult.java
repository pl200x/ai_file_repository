package com.example.file_management.agent;

import java.util.List;

/** Safe tool output: invisible hits never carry identifiers or content. */
public record ChunkSearchResult(
        int readableMatchCount,
        int hiddenMatchCount,
        List<AuthorizedChunk> chunks) {
}
