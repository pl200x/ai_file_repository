package com.example.file_management.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class KnowledgeRepository {
    private Long id;
    private String title;
    private String description;
    private int ownerId;
    //三个权限名单拆开存，方便之后按权限级别做集合操作
    private String writableList;
    private String readableList;
    private String manageableList;
    private Date createTime;
    private int tenantId;
    private boolean isPersonal;

    public KnowledgeRepository() {
    }
}
