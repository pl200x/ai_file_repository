package com.example.file_management.controller;

import com.example.file_management.controller.dto.NotificationPageDTO;
import com.example.file_management.controller.dto.NotificationReadDTO;
import com.example.file_management.controller.vo.DataVO;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {
    private static final Logger logger =
            LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    //铃铛角标专用：只回一个数，前端可以高频轮询
    @GetMapping("/unread_count")
    public DataVO<Integer> queryUnreadCount(@RequestParam int receiverId) {
        long start = System.currentTimeMillis();
        try {
            return success(start, notificationService.queryUnreadCount(receiverId));
        } catch (Exception exception) {
            return failure(start, exception, "query unread notification count");
        }
    }

    @GetMapping("/list")
    public DataVO<NotificationPageDTO> queryPage(
            @RequestParam int receiverId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        long start = System.currentTimeMillis();
        try {
            return success(start,
                    notificationService.queryPage(receiverId, page, pageSize));
        } catch (Exception exception) {
            return failure(start, exception, "query notification list");
        }
    }

    @PutMapping("/read")
    public DataVO<Void> markRead(
            @RequestBody NotificationReadDTO notificationReadDTO) {
        long start = System.currentTimeMillis();
        try {
            if (notificationReadDTO == null) {
                throw new IllegalArgumentException(
                        "notification read request is required");
            }
            notificationService.markRead(
                    notificationReadDTO.id(),
                    notificationReadDTO.receiverId(),
                    notificationReadDTO.read());
            return success(start, null);
        } catch (Exception exception) {
            return failure(start, exception, "update notification read status");
        }
    }

    private <T> DataVO<T> success(long start, T data) {
        return DataVO.buildDataVO(
                200, System.currentTimeMillis() - start, true, null, data);
    }

    private <T> DataVO<T> failure(
            long start, Exception exception, String operation) {
        long elapsed = System.currentTimeMillis() - start;
        if (exception instanceof IllegalArgumentException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    400, elapsed, false, exception.getMessage(), null);
        }
        if (exception instanceof UserPermissionDeniedException) {
            logger.warn("failed to {}: {}", operation, exception.getMessage());
            return DataVO.buildDataVO(
                    501, elapsed, false, exception.getMessage(), null);
        }
        logger.error("failed to " + operation, exception);
        return DataVO.buildDataVO(
                500, elapsed, false, "other unknown error", null);
    }
}
