package com.example.file_management.mapper;

import com.example.file_management.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {
    void addNotification(Notification notification);
    void addNotificationList(List<Notification> notificationList);
    Notification queryById(@Param("id") int id);
    int queryUnreadCount(@Param("read") boolean read,
                         @Param("receiverId") int receiverId);
    List<Notification> queryAll(@Param("receiverId") int receiverId,
                                @Param("startIndex") int startIndex,
                                @Param("pageSize") int pageSize);
    int queryTotalCount(@Param("receiverId") int receiverId);
    void updateReadStatus(@Param("id") int id,
                          @Param("read") boolean read);
}
