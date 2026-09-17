package com.example.file_management.mapper;

import com.example.file_management.entity.Chunk;
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
class ChunkMapperBindingTest {

    private static final String RESOURCE = "mapper/ChunkMapper.xml";
    //create_time is filled by the DB default, so inserts carry one column less
    private static final int INSERT_COLUMN_COUNT = 7;

    @Test
    void everyChunkMapperMethodHasMatchingXmlStatement() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);

        List<String> missing = new ArrayList<>();
        for (Method method : ChunkMapper.class.getDeclaredMethods()) {
            String statementId = ChunkMapper.class.getName() + "." + method.getName();
            if (!configuration.hasStatement(statementId)) {
                missing.add(method.getName());
            }
        }

        assertTrue(
                missing.isEmpty(),
                "ChunkMapper methods without matching XML statements: " + missing);
    }

    @Test
    void chunkIdMapsToLogicalKeyColumn() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        ResultMap resultMap = configuration.getResultMap("ChunkResultMap");

        assertEquals(Chunk.class, resultMap.getType());
        assertEquals("chunk_id", columnOf(resultMap, "chunkId"));
        assertEquals("chunk_index", columnOf(resultMap, "chunkIndex"));
        assertEquals("chunk_content", columnOf(resultMap, "chunkContent"));
    }

    @Test
    void batchInsertEmitsOneValuesTuplePerChunk() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        MappedStatement statement = configuration.getMappedStatement(
                ChunkMapper.class.getName() + ".addChunkList");

        //MyBatis wraps a bare List argument as {list: ...}, which is what
        //collection="list" in the XML resolves against at runtime.
        BoundSql boundSql = statement.getBoundSql(Map.of(
                "list", List.of(chunk(0), chunk(1), chunk(2))));

        assertEquals(
                3 * INSERT_COLUMN_COUNT,
                boundSql.getParameterMappings().size(),
                "batch insert must emit one tuple per chunk");
        //create_time must stay out of the column list: passing a null Date there
        //would violate the NOT NULL column instead of taking its DEFAULT
        assertTrue(!boundSql.getSql().toUpperCase().contains("CREATE_TIME"),
                boundSql.getSql());
    }

    /**
     * chunk_id is the Milvus doc_id, so the lookup that resolves vector hits
     * back to their text has to bind every id in the list; an IN () with no
     * elements would be invalid SQL.
     */
    @Test
    void queryByChunkIdBindsEveryId() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        MappedStatement statement = configuration.getMappedStatement(
                ChunkMapper.class.getName() + ".queryByChunkId");

        BoundSql boundSql = statement.getBoundSql(Map.of(
                "chunkIds", List.of("id-a", "id-b")));

        assertEquals(2, boundSql.getParameterMappings().size());
        assertTrue(boundSql.getSql().replaceAll("\\s+", " ").toUpperCase()
                .contains("CHUNK_ID IN ("), boundSql.getSql());
    }

    /**
     * The keyword half of the two-channel recall. Without a MATCH ... AGAINST
     * against the full text index this degrades into a LIKE-style table scan,
     * and natural language mode is what lets a raw user question be passed
     * through without escaping boolean operators.
     */
    @Test
    void keywordRecallGoesThroughTheFullTextIndex() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        MappedStatement statement = configuration.getMappedStatement(
                ChunkMapper.class.getName() + ".queryTopKByKeyword");

        BoundSql boundSql = statement.getBoundSql(Map.of("keyword", "问题", "limit", 12));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").toUpperCase();

        assertTrue(sql.contains("MATCH(CHUNK_CONTENT) AGAINST (? IN NATURAL LANGUAGE MODE)"),
                sql);
        //相关度降序 + LIMIT：service 层的 RRF 只认名次，顺序错了融合就没有意义
        assertTrue(sql.contains("ORDER BY MATCH"), sql);
        assertTrue(sql.contains("LIMIT ?"), sql);
        //keyword 绑定两次（WHERE 和 ORDER BY），加上 limit 一共三个占位符
        assertEquals(3, boundSql.getParameterMappings().size());
    }

    @Test
    void chunksOfOneFileAreReadInOriginalOrder() throws Exception {
        Configuration configuration = loadMapper(RESOURCE);
        MappedStatement statement = configuration.getMappedStatement(
                ChunkMapper.class.getName() + ".queryByRepositoryIdAndFileId");

        String sql = statement.getBoundSql(Map.of("repositoryId", 1, "fileId", 2))
                .getSql()
                .replaceAll("\\s+", " ")
                .toUpperCase();

        assertTrue(sql.contains("REPOSITORY_ID = ?"), sql);
        assertTrue(sql.contains("ORDER BY CHUNK_INDEX"), sql);
    }

    private static String columnOf(ResultMap resultMap, String property) {
        return resultMap.getResultMappings().stream()
                .filter(mapping -> property.equals(mapping.getProperty()))
                .map(ResultMapping::getColumn)
                .findFirst()
                .orElse(null);
    }

    private static Chunk chunk(int chunkIndex) {
        Chunk chunk = new Chunk();
        chunk.setFileId(1);
        chunk.setFileName("design doc");
        chunk.setChunkId("chunk-" + chunkIndex);
        chunk.setChunkContent("content");
        chunk.setOwnerId(10);
        chunk.setRepositoryId(1);
        chunk.setChunkIndex(chunkIndex);
        chunk.setCreateTime(new Date(0L));
        return chunk;
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
