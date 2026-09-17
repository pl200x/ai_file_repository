package com.example.file_management.controller.dto;

import lombok.Data;

import java.util.List;

//收件箱分页：total让前端能算总页数，unreadCount让列表和铃铛角标在一次请求里对齐
@Data
public class NotificationPageDTO {
    private List<NotificationDTO> notifications;
    private int total;
    private int unreadCount;
    private int page;
    private int pageSize;
}
