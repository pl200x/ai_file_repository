package com.example.file_management.service.producer;

import com.example.file_management.config.KafkaTopicConfig;
import com.example.file_management.entity.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class NotificationSendingProducer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationSendingProducer.class);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    //一次事件产生的全部收件人打成一条record：批量落库，且事件保持原子
    //key用targetType:targetId，同一目标的通知落同一分区保证顺序
    public void sendNotification(List<NotificationMessage> notificationMessageList) {
        if (notificationMessageList == null || notificationMessageList.isEmpty()) {
            return;
        }
        //一批消息来自同一次事件，因此共享同一个目标，分区键取第一条即可；
        //目标已经在NotificationMessage里，调用方不必再传一遍
        NotificationMessage first = notificationMessageList.get(0);
        String key = first.getTargetType() + ":" + first.getTargetId();
        String payload = objectMapper.writeValueAsString(notificationMessageList);

        kafkaTemplate.send(KafkaTopicConfig.NOTIFICATION_SENDING, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        //send是异步的，失败不影响权限申请主流程，记日志人工兜底
                        //异常必须作为最后一个独立参数，塞进占位符会丢掉堆栈
                        logger.error("failed to publish notification message for {}", key, ex);
                    }
                });
        logger.info("message has been sent:" + payload);
    }
}
