package com.example.file_management.service;

import com.example.file_management.controller.dto.NotificationPageDTO;

public interface NotificationService {
    int queryUnreadCount(int receiverId);

    NotificationPageDTO queryPage(int receiverId, int page, int pageSize);

    void markRead(int id, int receiverId, boolean read);
}
