package com.example.file_management.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Chunk {
    private int id;
    private int fileId;
    private String fileName;
    //logic primary key
    private String chunkId;
    private String chunkContent;
    private int ownerId;
    private int repositoryId;
    private int chunkIndex;
    private Date createTime;
}
