package com.example.file_management.controller.vo;

import lombok.Data;

import java.util.Date;

@Data
public class UserPermissionVO {
    private int permissionId;
    private int userId;
    private String targetType;
    private int targetId;
    private String profile;
    private String name;
    private String email;
    private String permissionType;
    private String status;
    private Date expirationTime;
}
