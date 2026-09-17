-- 权限申请提醒：通知落库表。
--
-- 写入方是 NotificationSendingConsumer（Kafka topic notification-sending），
-- 读取方是未读计数与收件箱分页查询。
--
-- target_id 是多态外键：target_type = FILE 时指向 files.id，
-- = KNOWLEDGE_REPOSITORY 时指向 knowledge_repositories.id，
-- 两张表主键类型不同，因此这一列不加外键约束，由应用层保证。
CREATE TABLE `notifications` (
    `id`                int          NOT NULL AUTO_INCREMENT,
    `topic`             varchar(32)  NOT NULL COMMENT 'NotificationTopic code: APPLY_PERMISSION / LIKE / COMMENT',
    `applicant`         int          NOT NULL COMMENT '触发事件的用户',
    `receiver_id`       int          NOT NULL COMMENT '收到提醒的用户',
    `target_type`       varchar(32)  NOT NULL COMMENT 'NotificationTargetType code: FILE / KNOWLEDGE_REPOSITORY',
    `target_id`         int          NOT NULL COMMENT '多态引用，见表注释',
    `operation_content` varchar(512) DEFAULT NULL COMMENT '展示文案，由生产端拼好',
    `operation_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间，由生产端写入而非消费时间',
    -- read 是 MySQL 保留字，列名用 is_read，由 resultMap 映射回实体的 read 属性
    `is_read`           tinyint(1)   NOT NULL DEFAULT 0 COMMENT '0 = 未读, 1 = 已读',
    PRIMARY KEY (`id`),
    -- 未读计数：WHERE receiver_id = ? AND is_read = ?
    KEY `idx_notifications_receiver_read` (`receiver_id`, `is_read`),
    -- 收件箱分页：WHERE receiver_id = ? ORDER BY operation_time DESC
    KEY `idx_notifications_receiver_time` (`receiver_id`, `operation_time`),
    CONSTRAINT `fk_notifications_applicant`
        FOREIGN KEY (`applicant`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_notifications_receiver`
        FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
