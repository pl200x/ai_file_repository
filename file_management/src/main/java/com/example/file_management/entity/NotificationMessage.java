package com.example.file_management.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

//MQ传输契约：只带业务语义字段。id和read是存储侧职责，不出现在这里
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {
    private String topic;
    private int applicant;
    private int receiverId;
    private String targetType;
    private int targetId;
    private String operationContent;
    //事件时间由生产端写入：消息重试或滞留DLT后再消费，消费时间会漂移
    private Date operationTime;
}
