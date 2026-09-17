package com.example.file_management.service;

import com.example.file_management.controller.vo.ChunkVO;
import com.example.file_management.entity.Chunk;
import java.util.List;

public interface ChunkService {
    void addChunk(Chunk chunk);
    void addChunkList(List<Chunk> chunkList);
    Chunk queryById(int id);

    List<Chunk> queryByRepositoryIdAndFileId(int repositoryId,
                                             int fileId);

    List<Chunk> queryByChunkId(List<String> chunkIds);
    //not considering updaate since complicated for re-slice/double check total chunk counts are the same
    //void batchUpdate(List<Chunk> chunkList);
    void deleteAllChunk(int repositoryId,
                        int fileId);
    List<ChunkVO> queryTopKSimilarity(int k, String userInput, int userId);
}
