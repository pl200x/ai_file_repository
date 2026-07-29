package com.example.file_management.config;


import com.example.file_management.exception.UserNotExistException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorConfig {

    //TODO:写DeadLetterConsumer时把这个常量挪到公共位置，两边引用同一份
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // 显式指定死信 topic 后缀,与 DeadLetterConsumer 订阅的常量对齐
        // (spring-kafka 各版本默认后缀不一致,.DLT / -dlt 都出现过,不能依赖默认值)
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 2L));
        errorHandler.addNotRetryableExceptions(
                UserNotExistException.class);
        return errorHandler;
    }
}