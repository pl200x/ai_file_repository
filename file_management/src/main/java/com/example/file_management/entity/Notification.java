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
public class Notification {
    private int id;
    //apply, like
    private String topic;
    private int applicant;
    private int receiverId;
    //target repository/file
    private String targetType;
    private int targetId;
    //read or write content
    //if like, operationContent set null
    //if comment, operationContent is comment content
    private String operationContent;
    private Date operationTime;
    private boolean read;
}
