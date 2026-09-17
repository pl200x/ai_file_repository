package com.example.file_management.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//检索命中的片段。无权限的命中也会返回（让调用方知道"还有N条你看不到"），
//但那时fileName和chunkContent一律为null，否则可见性标记就只是装饰
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChunkVO {
    private int id;
    private int fileId;
    private String fileName;
    //logic primary key
    private String chunkId;
    private String chunkContent;
    private int ownerId;
    private int repositoryId;
    private int chunkIndex;
    private boolean isVisible;
}
