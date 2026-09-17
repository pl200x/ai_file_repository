package com.example.file_management.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    //按事件类型分topic：授权和删除是不同的业务事件，消费方、重试策略、DLT都独立
    public static final String PERMISSION_BATCH_GIVING_TOPIC = "permission-batch-giving";
    public static final String NOTIFICATION_SENDING = "notification-sending";
    public static final String CHUNK_SPLITTING_TOPIC = "chunk-splitting";

    @Bean
    public NewTopic permissionBatchGivingTopic() {
        return TopicBuilder.name(PERMISSION_BATCH_GIVING_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
    @Bean
    public NewTopic notificationSending() {
        return TopicBuilder.name(NOTIFICATION_SENDING)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chunkSplittingTopic() {
        return TopicBuilder.name(CHUNK_SPLITTING_TOPIC)
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

    @Bean
    public NewTopic notificationSendingDltTopic() {
        return TopicBuilder.name(NOTIFICATION_SENDING+ KafkaErrorConfig.DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chunkSplittingDltTopic() {
        return TopicBuilder.name(CHUNK_SPLITTING_TOPIC + KafkaErrorConfig.DLT_SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
