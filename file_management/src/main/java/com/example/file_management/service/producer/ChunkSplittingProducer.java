package com.example.file_management.service.producer;

import com.example.file_management.config.KafkaTopicConfig;
import com.example.file_management.entity.ChunkSplittingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ChunkSplittingProducer {
    private static final Logger logger = LoggerFactory.getLogger(ChunkSplittingProducer.class);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    //key用fileId：同一文件的切分事件落同一分区，重复投递按顺序消费，
    //最后一次切分的结果就是最终结果，不会出现旧正文覆盖新正文
    public void sendChunkSplitting(ChunkSplittingMessage chunkSplittingMessage) {
        if (chunkSplittingMessage == null) {
            return;
        }
        //没有正文就没有可切的片段，不必占用一次消费
        if (chunkSplittingMessage.getContent() == null
                || chunkSplittingMessage.getContent().isBlank()) {
            logger.info("file {} has no content to split, skipped",
                    chunkSplittingMessage.getFileId());
            return;
        }
        int fileId = chunkSplittingMessage.getFileId();
        String payload = objectMapper.writeValueAsString(chunkSplittingMessage);

        kafkaTemplate.send(KafkaTopicConfig.CHUNK_SPLITTING_TOPIC, String.valueOf(fileId), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        //send是异步的，失败不影响addFile主流程，记日志人工兜底
                        //异常必须作为最后一个独立参数，塞进占位符会丢掉堆栈
                        logger.error("failed to publish chunk splitting message for file {}", fileId, ex);
                    }
                });
        logger.info("chunk splitting message has been sent for file {}", fileId);
    }
}
