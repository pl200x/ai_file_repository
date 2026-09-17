package com.example.file_management.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChunkSplitterConfig {

    //切分粒度直接决定检索质量，走配置而不是硬编码：
    //chunkSize按token计（和embedding模型同一套计量），minChunkSizeChars是回退到
    //按标点断句时片段的最短字符数，minChunkLengthToEmbed过滤掉过短的碎片
    @Bean
    public TokenTextSplitter tokenTextSplitter(
            @Value("${rag.chunk.token-size:800}") int chunkSize,
            @Value("${rag.chunk.min-chars:350}") int minChunkSizeChars,
            @Value("${rag.chunk.min-length-to-embed:5}") int minChunkLengthToEmbed,
            @Value("${rag.chunk.max-num:10000}") int maxNumChunks,
            @Value("${rag.chunk.keep-separator:true}") boolean keepSeparator) {
        return TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(minChunkLengthToEmbed)
                .withMaxNumChunks(maxNumChunks)
                .withKeepSeparator(keepSeparator)
                //默认标点表只有英文句读，中文正文会整段找不到断点退化成硬切，
                //补上中文标点；英文的也要一并列出，这里是整体覆盖而不是追加
                .withPunctuationMarks(List.of(
                        '.', '?', '!', '\n',
                        '。', '？', '！', '；', '．'))
                .build();
    }
}
