package com.example.file_management.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class User {
    private int id;
    private String profile;
    private String name;
    private String email;
    //企业管理
    private int tenantId;
    private int groupId;
    private Date createTime;

    public User() {
    }
}
