package com.example.permission.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDTO {
    private int userId;

    //目标对象的id（文档id或知识库id）
    private int targetId;

    //目标类型：PermissionTargetType 的 code（FILE / KNOWLEDGE_REPOSITORY）
    private String type;

    //申请的权限级别：readable / writable / manageable
    private String permission;

    //有效时长（毫秒），从当前时间开始计算
    private long expirationTime;

}
