package com.example.file_management.controller.dto;

//receiverId不是冗余：标记已读前要校验这条通知确实属于该用户，否则任何人都能改别人的通知
public record NotificationReadDTO(int id, int receiverId, boolean read) {
}
