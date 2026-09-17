package com.example.file_management.controller.dto;

import lombok.Data;

import java.util.Date;

//通知的对外形状：controller只认这个，不把Notification实体（及其DB列语义）暴露出去。
//applicantName/applicantProfile/targetTitle是读侧补齐的展示字段，实体里没有。
@Data
public class NotificationDTO {
    private int id;
    //NotificationTopic的code：APPLY_PERMISSION / LIKE / COMMENT
    private String topic;
    private int applicantId;
    private String applicantName;
    private String applicantProfile;
    private int receiverId;
    //NotificationTargetType的code：FILE / KNOWLEDGE_REPOSITORY
    private String targetType;
    private int targetId;
    private String targetTitle;
    private String operationContent;
    private Date operationTime;
    private boolean read;
}
