package com.example.file_management.service.producer;

import com.example.file_management.config.KafkaTopicConfig;
import com.example.file_management.integration.PermissionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class PermissionBatchGivingProducer {
    private static final Logger logger = LoggerFactory.getLogger(PermissionBatchGivingProducer.class);

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    //key用targetId：同一文件的授权事件落同一分区，保证顺序
    public void sendBatchGiving(List<PermissionDTO> permissionDTOList) {
        if (permissionDTOList == null || permissionDTOList.isEmpty()) {
            return;
        }
        //一批授权来自同一次文件创建，因此共享同一个targetId，分区键取第一条即可；
        //targetId已经在PermissionDTO里，调用方不必再传一遍
        int targetId = permissionDTOList.get(0).getTargetId();
        String payload = objectMapper.writeValueAsString(permissionDTOList);

        kafkaTemplate.send(KafkaTopicConfig.PERMISSION_BATCH_GIVING_TOPIC, String.valueOf(targetId), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        //send是异步的，失败不影响addFile主流程，记日志人工兜底
                        logger.error("failed to publish batch giving message for file {}", targetId, ex);
                    }
                });
    }
}
