package com.example.file_management.service.consumer;

import com.example.file_management.config.KafkaTopicConfig;
import com.example.file_management.entity.Chunk;
import com.example.file_management.entity.ChunkSplittingMessage;
import com.example.file_management.service.ChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class ChunkSplittingConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ChunkSplittingConsumer.class);

    //向量库metadata的key：检索时按fileId/repositoryId/ownerId过滤做权限感知，
    //命中后拿chunkId回chunks表取原文，两边字段名保持一致
    public static final String METADATA_CHUNK_ID = "chunkId";
    public static final String METADATA_FILE_ID = "fileId";
    public static final String METADATA_FILE_NAME = "fileName";
    public static final String METADATA_OWNER_ID = "ownerId";
    public static final String METADATA_REPOSITORY_ID = "repositoryId";
    public static final String METADATA_CHUNK_INDEX = "chunkIndex";

    @Autowired
    private ChunkService chunkService;
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private TokenTextSplitter tokenTextSplitter;
    @Autowired
    private ObjectMapper objectMapper;

    //抛出的异常交给KafkaErrorConfig的DefaultErrorHandler：重试2次后进DLT
    @KafkaListener(topics = KafkaTopicConfig.CHUNK_SPLITTING_TOPIC)
    public void onChunkSplitting(String payload) {
        ChunkSplittingMessage message =
                objectMapper.readValue(payload, ChunkSplittingMessage.class);
        String content = message.getContent();
        //生产端已挡过一次，重试/DLT重放的老消息可能仍是空正文
        if (content == null || content.isBlank()) {
            logger.warn("file {} has no content to split, skipped", message.getFileId());
            return;
        }

        List<Document> splitDocuments = tokenTextSplitter.split(new Document(content));
        if (splitDocuments.isEmpty()) {
            logger.warn("file {} produced no chunk, skipped", message.getFileId());
            return;
        }

        //重试或重复投递会把同一篇切第二遍：先按(repositoryId, fileId)清掉两边的旧数据，
        //消费整体保持幂等，也避免撞uk_chunks_file_index
        deleteExistingChunks(message);

        //chunkId一次生成两处共用：Milvus的doc_id和chunks.chunk_id，
        //向量检索命中后靠它回表取原文
        List<Document> vectorDocuments = new ArrayList<>();
        List<Chunk> chunks = new ArrayList<>();
        Date splitTime = new Date();
        for (int chunkIndex = 0; chunkIndex < splitDocuments.size(); chunkIndex++) {
            String chunkContent = splitDocuments.get(chunkIndex).getText();
            String chunkId = UUID.randomUUID().toString();
            vectorDocuments.add(Document.builder()
                    .id(chunkId)
                    .text(chunkContent)
                    .metadata(METADATA_CHUNK_ID, chunkId)
                    .metadata(METADATA_FILE_ID, message.getFileId())
                    .metadata(METADATA_FILE_NAME, message.getFileName())
                    .metadata(METADATA_OWNER_ID, message.getOwnerId())
                    .metadata(METADATA_REPOSITORY_ID, message.getRepositoryId())
                    .metadata(METADATA_CHUNK_INDEX, chunkIndex)
                    .build());
            chunks.add(buildChunk(message, chunkId, chunkContent, chunkIndex, splitTime));
        }

        //先写向量库再写MySQL：chunks里有记录就意味着向量已经落库，
        //反过来（向量存在而MySQL没有）会在下一次重试时被deleteExistingChunks清掉
        //embedding调用耗时不可控（大文档/Milvus抖动都会拖慢），处理前后各打一行日志，
        //避免再次出现"卡住且没有任何日志"、只能靠翻consumer group元数据才能定位的情况
        logger.info("embedding {} chunks into vector store for file {}",
                vectorDocuments.size(), message.getFileId());
        vectorStore.add(vectorDocuments);
        chunkService.addChunkList(chunks);
        logger.info("consuming chunk splitting message, file {} split into {} chunks",
                message.getFileId(), chunks.size());
    }

    //两个存储都按同一个(repositoryId, fileId)清：MySQL走索引删除，
    //Milvus没有二级索引概念，按metadata过滤表达式删
    private void deleteExistingChunks(ChunkSplittingMessage message) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression expression = builder.and(
                builder.eq(METADATA_REPOSITORY_ID, message.getRepositoryId()),
                builder.eq(METADATA_FILE_ID, message.getFileId())).build();
        vectorStore.delete(expression);
        chunkService.deleteAllChunk(message.getRepositoryId(), message.getFileId());
    }

    private Chunk buildChunk(ChunkSplittingMessage message,
                             String chunkId,
                             String chunkContent,
                             int chunkIndex,
                             Date splitTime) {
        Chunk chunk = new Chunk();
        chunk.setFileId(message.getFileId());
        chunk.setFileName(message.getFileName());
        chunk.setChunkId(chunkId);
        chunk.setChunkContent(chunkContent);
        chunk.setOwnerId(message.getOwnerId());
        chunk.setRepositoryId(message.getRepositoryId());
        chunk.setChunkIndex(chunkIndex);
        //同一批片段共用一个切分时间；create_time列由DB的DEFAULT填充，
        //这里赋值只为返回给调用方的对象是完整的
        chunk.setCreateTime(splitTime);
        return chunk;
    }
}
