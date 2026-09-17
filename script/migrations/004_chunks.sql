-- RAG 文本切分落库：一个文件切成多个 chunk，正文与向量分开存。
--
-- 写入方是 ChunkSplittingConsumer（Kafka topic chunk-splitting）：
-- 先把切好的 chunk 写进 Milvus 向量库，再把同一批正文写进本表，
-- 两边用 chunk_id（UUID）对齐 —— 向量检索命中后拿 chunk_id 回本表取原文。
--
-- 消费端重试/重投递前会按 (repository_id, file_id) 整体删除再重写，
-- 因此本表不做 upsert，靠 uk_chunks_chunk_id 兜住重复写入。
CREATE TABLE `chunks` (
    `id`            int          NOT NULL AUTO_INCREMENT,
    `file_id`       int          NOT NULL COMMENT '引用 files.id',
    `file_name`     varchar(256) NOT NULL COMMENT '切分时的文件标题，冗余一份避免检索结果再回表 files',
    `chunk_id`      varchar(64)  NOT NULL COMMENT '逻辑主键，同时是 Milvus 里的 doc_id',
    `chunk_content` text         NOT NULL COMMENT '切分后的正文片段',
    `owner_id`      int          NOT NULL COMMENT '引用 users.id',
    -- 与 files.repository_id 对齐：knowledge_repositories.id 是 BIGINT，外键要求两端同类型
    `repository_id` bigint       NOT NULL COMMENT '引用 knowledge_repositories.id',
    `chunk_index`   int          NOT NULL COMMENT '片段在原文里的顺序，从 0 开始',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '由 DB 填充，插入语句不带这一列',
    PRIMARY KEY (`id`),
    -- 逻辑主键：向量库回表按 chunk_id 查，同时挡住消费重试造成的重复插入
    UNIQUE KEY `uk_chunks_chunk_id` (`chunk_id`),
    -- 同一文件的片段顺序唯一，重复切分会撞键而不是静默产生两份
    UNIQUE KEY `uk_chunks_file_index` (`file_id`, `chunk_index`),
    -- 按库+文件查全部片段 / 删除整篇：queryByRepositoryIdAndFileId、deleteAllChunk
    KEY `idx_chunks_repo_file` (`repository_id`, `file_id`),
    CONSTRAINT `fk_chunks_file`
        FOREIGN KEY (`file_id`) REFERENCES `files` (`id`),
    CONSTRAINT `fk_chunks_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_chunks_repo`
        FOREIGN KEY (`repository_id`) REFERENCES `knowledge_repositories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
