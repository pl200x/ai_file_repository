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
public class File {
    //content原样保留上传/编辑时的文本；contentFormat只影响前端按什么格式渲染，
    //不影响切分/检索链路——chunk消费者对两种格式一视同仁
    public static final String CONTENT_FORMAT_PLAIN = "PLAIN";
    public static final String CONTENT_FORMAT_MARKDOWN = "MARKDOWN";

    private int id;
    private int repositoryId;
    private int ownerId;
    //title is unique
    private String title;
    private String content;
    private String contentFormat;
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

}
