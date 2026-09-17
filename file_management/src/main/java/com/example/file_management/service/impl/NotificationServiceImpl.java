package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.NotificationDTO;
import com.example.file_management.controller.dto.NotificationPageDTO;
import com.example.file_management.entity.File;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.entity.Notification;
import com.example.file_management.entity.User;
import com.example.file_management.enums.NotificationTargetType;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.mapper.NotificationMapper;
import com.example.file_management.service.KnowledgeRepositoryService;
import com.example.file_management.service.NotificationService;
import com.example.file_management.service.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final boolean UNREAD = false;

    private final NotificationMapper notificationMapper;
    private final UserService userService;
    private final KnowledgeRepositoryService knowledgeRepositoryService;
    private final FileMapper fileMapper;

    public NotificationServiceImpl(
            NotificationMapper notificationMapper,
            UserService userService,
            KnowledgeRepositoryService knowledgeRepositoryService,
            FileMapper fileMapper) {
        this.notificationMapper = notificationMapper;
        this.userService = userService;
        this.knowledgeRepositoryService = knowledgeRepositoryService;
        this.fileMapper = fileMapper;
    }

    @Override
    public int queryUnreadCount(int receiverId) {
        requirePositive(receiverId, "receiverId");
        return notificationMapper.queryUnreadCount(UNREAD, receiverId);
    }

    @Override
    public NotificationPageDTO queryPage(int receiverId, int page, int pageSize) {
        requirePositive(receiverId, "receiverId");
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int startIndex = (normalizedPage - 1) * normalizedPageSize;

        List<Notification> notifications = notificationMapper.queryAll(
                receiverId, startIndex, normalizedPageSize);

        NotificationPageDTO pageDTO = new NotificationPageDTO();
        pageDTO.setNotifications(toDTOList(notifications));
        pageDTO.setTotal(notificationMapper.queryTotalCount(receiverId));
        pageDTO.setUnreadCount(notificationMapper.queryUnreadCount(UNREAD, receiverId));
        pageDTO.setPage(normalizedPage);
        pageDTO.setPageSize(normalizedPageSize);
        return pageDTO;
    }

    @Override
    public void markRead(int id, int receiverId, boolean read) {
        requirePositive(id, "id");
        requirePositive(receiverId, "receiverId");
        Notification notification = notificationMapper.queryById(id);
        if (notification == null) {
            throw new IllegalArgumentException("The notification does not exist");
        }
        //只有收件人自己能改这条通知的已读状态，否则任何人都能改别人的
        if (notification.getReceiverId() != receiverId) {
            throw new UserPermissionDeniedException(
                    "You can only update your own notifications");
        }
        notificationMapper.updateReadStatus(id, read);
    }

    //展示字段一次性补齐：申请人按id批量查，目标标题按类型去重后查，避免逐条N+1
    private List<NotificationDTO> toDTOList(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }

        List<Integer> applicantIds = notifications.stream()
                .map(Notification::getApplicant)
                .distinct()
                .toList();
        Map<Integer, User> userById = new LinkedHashMap<>();
        for (User user : userService.queryByIds(applicantIds)) {
            userById.put(user.getId(), user);
        }

        Map<String, String> titleByTargetKey = resolveTargetTitles(notifications);

        List<NotificationDTO> dtoList = new ArrayList<>();
        for (Notification notification : notifications) {
            dtoList.add(toDTO(
                    notification,
                    userById.get(notification.getApplicant()),
                    titleByTargetKey.get(targetKey(
                            notification.getTargetType(), notification.getTargetId()))));
        }
        return dtoList;
    }

    //一页最多MAX_PAGE_SIZE条，去重后目标数远小于此；
    //FileMapper/KnowledgeRepositoryService都没有批量按id查标题的方法，
    //这里先按去重后的目标逐个查，需要时再补批量查询
    private Map<String, String> resolveTargetTitles(List<Notification> notifications) {
        Map<String, String> titleByTargetKey = new LinkedHashMap<>();
        for (Notification notification : notifications) {
            String key = targetKey(
                    notification.getTargetType(), notification.getTargetId());
            if (titleByTargetKey.containsKey(key)) {
                continue;
            }
            titleByTargetKey.put(key, queryTargetTitle(
                    notification.getTargetType(), notification.getTargetId()));
        }
        return titleByTargetKey;
    }

    private String queryTargetTitle(String targetType, int targetId) {
        if (NotificationTargetType.KNOWLEDGE_REPOSITORY.getCode()
                .equalsIgnoreCase(targetType)) {
            KnowledgeRepository repository =
                    knowledgeRepositoryService.queryById(targetId);
            return repository == null ? null : repository.getTitle();
        }
        File file = fileMapper.queryById(targetId);
        return file == null ? null : file.getTitle();
    }

    private String targetKey(String targetType, int targetId) {
        return targetType + ":" + targetId;
    }

    //目标或申请人可能已被删除，标题和姓名留空由前端兜底，不因此让整页查询失败
    private NotificationDTO toDTO(
            Notification notification, User applicant, String targetTitle) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());
        dto.setTopic(notification.getTopic());
        dto.setApplicantId(notification.getApplicant());
        dto.setApplicantName(applicant == null ? null : applicant.getName());
        dto.setApplicantProfile(applicant == null ? null : applicant.getProfile());
        dto.setReceiverId(notification.getReceiverId());
        dto.setTargetType(notification.getTargetType());
        dto.setTargetId(notification.getTargetId());
        dto.setTargetTitle(targetTitle);
        dto.setOperationContent(notification.getOperationContent());
        dto.setOperationTime(notification.getOperationTime());
        dto.setRead(notification.isRead());
        return dto;
    }

    private void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
