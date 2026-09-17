package com.example.file_management.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//MQ传输契约：只带切分需要的字段。chunkId、chunkIndex、createTime由消费端切分时生成，
//是存储侧职责，不出现在这里
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSplittingMessage {
    private int fileId;
    //切分时的文件标题，随片段一起冗余进chunks和向量库的metadata
    private String fileName;
    //待切分的正文全文
    private String content;
    private int ownerId;
    private int repositoryId;
}
