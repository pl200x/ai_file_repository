package com.example.file_management.mapper;


import com.example.file_management.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChunkMapper {

    void addChunk(Chunk chunk);
    void addChunkList(List<Chunk> chunkList);
    Chunk queryById(@Param("id") int id);

    List<Chunk> queryByRepositoryIdAndFileId(@Param("repositoryId") int repositoryId,
                                @Param("fileId") int fileId);


    //chunkId是Milvus里的doc_id，varchar(64)的UUID，不是自增主键
    List<Chunk> queryByChunkId(@Param("chunkIds") List<String> chunkIds);

    //两路召回里的关键字这一路：走chunk_content上的ngram全文索引，按相关度取前limit条。
    //和向量召回并列，两边的名次在service层用RRF融合，这里只负责给出一个有序候选集
    List<Chunk> queryTopKByKeyword(@Param("keyword") String keyword,
                                   @Param("limit") int limit);
    //not considering updaate since complicated for re-slice/double check total chunk counts are the same
    //void batchUpdate(List<Chunk> chunkList);
    void deleteAllChunk(@Param("repositoryId") int repositoryId,
                        @Param("fileId") int fileId);
}
