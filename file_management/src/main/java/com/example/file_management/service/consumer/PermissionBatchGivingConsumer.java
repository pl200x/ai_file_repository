package com.example.file_management.service.consumer;

import com.example.file_management.config.KafkaTopicConfig;
import com.example.file_management.integration.PermissionDTO;
import com.example.file_management.integration.PermissionIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class PermissionBatchGivingConsumer {
    private static final Logger logger = LoggerFactory.getLogger(PermissionBatchGivingConsumer.class);

    @Autowired
    private PermissionIntegration permissionIntegration;
    @Autowired
    private ObjectMapper objectMapper;

    //抛出的异常交给KafkaErrorConfig的DefaultErrorHandler：重试2次后进DLT
    @KafkaListener(topics = KafkaTopicConfig.PERMISSION_BATCH_GIVING_TOPIC)
    public void onBatchGiving(String payload) {
        List<PermissionDTO> permissionDTOList =
                objectMapper.readValue(payload, new TypeReference<List<PermissionDTO>>() {});
        logger.info("consuming batch giving message, {} permissions", permissionDTOList.size());
        permissionIntegration.batchGivingPermission(permissionDTOList);
    }
}
