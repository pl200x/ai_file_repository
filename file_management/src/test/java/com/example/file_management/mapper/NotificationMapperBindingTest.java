package com.example.file_management.mapper;

import com.example.file_management.entity.Notification;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catches interface/XML drift before MyBatis can fail at runtime with
 * BindingException: Invalid bound statement (not found).
 */
class NotificationMapperBindingTest {

    private static final String RESOURCE = "mapper/NotificationMapper.xml";
    private static final int COLUMN_COUNT = 8;

    @Test
    void everyNotificationMapperMethodHasMatchingXmlStatement() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);

        List<String> missing = new ArrayList<>();
        for (Method method : NotificationMapper.class.getDeclaredMethods()) {
            String statementId =
                    NotificationMapper.class.getName() + "." + method.getName();
            if (!configuration.hasStatement(statementId)) {
                missing.add(method.getName());
            }
        }

        assertTrue(
                missing.isEmpty(),
                "NotificationMapper methods without matching XML statements: "
                        + missing);
    }

    /**
     * read is a MySQL reserved word, so the column is is_read. If the mapping
     * ever drifts back to a read column the entity silently reads false.
     */
    @Test
    void readPropertyMapsToIsReadColumn() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        ResultMap resultMap = configuration.getResultMap("NotificationResultMap");

        assertEquals(Notification.class, resultMap.getType());
        String column = resultMap.getResultMappings().stream()
                .filter(mapping -> "read".equals(mapping.getProperty()))
                .map(ResultMapping::getColumn)
                .findFirst()
                .orElse(null);
        assertEquals("is_read", column);
    }

    @Test
    void batchInsertEmitsOneValuesTuplePerNotification() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        MappedStatement statement = configuration.getMappedStatement(
                NotificationMapper.class.getName() + ".addNotificationList");

        //MyBatis wraps a bare List argument as {list: ...}, which is what
        //collection="list" in the XML resolves against at runtime.
        BoundSql boundSql = statement.getBoundSql(Map.of(
                "list", List.of(notification(), notification(), notification())));

        assertEquals(
                3 * COLUMN_COUNT,
                boundSql.getParameterMappings().size(),
                "batch insert must emit one tuple per notification");
        assertTrue(boundSql.getSql().toUpperCase().contains("IS_READ"));
    }

    @Test
    void inboxPageIsOrderedNewestFirst() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        MappedStatement statement = configuration.getMappedStatement(
                NotificationMapper.class.getName() + ".queryAll");

        String sql = statement.getBoundSql(Map.of(
                        "receiverId", 1,
                        "startIndex", 0,
                        "pageSize", 20))
                .getSql()
                .replaceAll("\\s+", " ")
                .toUpperCase();

        assertTrue(sql.contains("ORDER BY OPERATION_TIME DESC, ID DESC"), sql);
        assertTrue(sql.contains("RECEIVER_ID = ?"), sql);
    }

    private static Notification notification() {
        Notification notification = new Notification();
        notification.setTopic("APPLY_PERMISSION");
        notification.setApplicant(1);
        notification.setReceiverId(2);
        notification.setTargetType("FILE");
        notification.setTargetId(3);
        notification.setOperationContent("content");
        notification.setOperationTime(new Date(0L));
        notification.setRead(false);
        return notification;
    }

    private static Configuration loadMapper(String resource) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    in,
                    configuration,
                    resource,
                    configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }
}
