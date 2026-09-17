package com.example.file_management.service.consumer;

import com.example.file_management.config.KafkaTopicConfig;
import com.example.file_management.entity.Notification;
import com.example.file_management.entity.NotificationMessage;
import com.example.file_management.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class NotificationSendingConsumer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationSendingConsumer.class);

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private ObjectMapper objectMapper;

    //抛出的异常交给KafkaErrorConfig的DefaultErrorHandler：重试2次后进DLT
    @KafkaListener(topics = KafkaTopicConfig.NOTIFICATION_SENDING)
    public void onNotificationSending(String payload) {
        logger.info("start consuming");
        List<NotificationMessage> notificationMessageList =
                objectMapper.readValue(payload, new TypeReference<List<NotificationMessage>>() {});
        //空列表会让mapper的foreach生成VALUES后无内容的非法SQL
        if (notificationMessageList.isEmpty()) {
            logger.warn("received empty notification message list, skipped");
            return;
        }
        //batch insert notification to database
        List<Notification> notifyList = convertNotificationMessageToNotification(notificationMessageList);
        notificationMapper.addNotificationList(notifyList);
        logger.info("consuming notification message, {} notifications", notifyList.size());
    }

    private List<Notification> convertNotificationMessageToNotification(
            List<NotificationMessage> notificationMessageList) {
        List<Notification> returnList = new ArrayList<>();
        for (NotificationMessage each : notificationMessageList) {
            returnList.add(convertSingleNotificationMessageToNotification(each));
        }
        return returnList;
    }

    private Notification convertSingleNotificationMessageToNotification(
            NotificationMessage notificationMessage) {
        Notification notification = new Notification();
        notification.setTopic(notificationMessage.getTopic());
        notification.setApplicant(notificationMessage.getApplicant());
        notification.setReceiverId(notificationMessage.getReceiverId());
        notification.setTargetType(notificationMessage.getTargetType());
        notification.setTargetId(notificationMessage.getTargetId());
        notification.setOperationContent(notificationMessage.getOperationContent());
        //用生产端带来的事件时间，不是消费时间
        notification.setOperationTime(notificationMessage.getOperationTime());
        //新落库的通知一律未读，id由DB自增
        notification.setRead(false);
        return notification;
    }
}
