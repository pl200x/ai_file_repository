package com.example.file_management.entity;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Group {
    private int id;
    private int tenantId;
    private String name;
    private String profile;
    private Long managerId;
    private Date createdAt;
}
