package com.example.file_management.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    //按事件类型分topic：授权和删除是不同的业务事件，消费方、重试策略、DLT都独立
    public static final String PERMISSION_BATCH_GIVING_TOPIC = "permission-batch-giving";

    @Bean
    public NewTopic permissionBatchGivingTopic() {
        return TopicBuilder.name(PERMISSION_BATCH_GIVING_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    //DLT分区数必须>=主topic：KafkaErrorConfig的recoverer按record.partition()原分区投递
    @Bean
    public NewTopic permissionBatchGivingDltTopic() {
        return TopicBuilder.name(PERMISSION_BATCH_GIVING_TOPIC + KafkaErrorConfig.DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
