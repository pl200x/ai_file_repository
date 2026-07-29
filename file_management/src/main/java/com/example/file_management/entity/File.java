package com.example.file_management.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class File {
    private int id;
    private int repositoryId;
    private int ownerId;
    //title is unique
    private String title;
    private String content;
    //TODO:writableList 存 有且编辑过文档的人
    //readable List存 有且读过文档的人
    //manageable List存当前repository所有manageable的人以及owner本人
    private String writerList;
    private String readerList;
    private String manageableList;
    private Date publishTime;
    private Date recentUpdateTime;
    private int latestModifiedUserId;
    private int tenantId;
    private boolean isPrivate;
    private boolean isDeleted;


    public File() {
    }
}
