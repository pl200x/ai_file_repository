package com.example.file_management.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catches interface/XML drift before MyBatis can fail at runtime with
 * BindingException: Invalid bound statement (not found).
 */
class FileMapperBindingTest {

    @Test
    void everyFileMapperMethodHasMatchingXmlStatement() throws Exception {
        Configuration configuration = loadMapper("mapper/FileMapper.xml");
        assertEveryMethodIsBound(configuration, FileMapper.class);
    }

    @Test
    void everyFileVersionMapperMethodHasMatchingXmlStatement() throws Exception {
        Configuration configuration = loadMapper("mapper/FileVersionMapper.xml");
        assertEveryMethodIsBound(configuration, FileVersionMapper.class);
    }

    @Test
    void addFileUsesDatabaseGeneratedId() throws Exception {
        Configuration configuration = loadMapper("mapper/FileMapper.xml");
        MappedStatement statement = configuration.getMappedStatement(
                FileMapper.class.getName() + ".addFile");

        assertInstanceOf(Jdbc3KeyGenerator.class, statement.getKeyGenerator());
        assertArrayEquals(new String[]{"id"}, statement.getKeyProperties());
    }

    @Test
    void versionChainWriteQueryLocksLatestVersion() throws Exception {
        Configuration configuration = loadMapper("mapper/FileVersionMapper.xml");
        MappedStatement statement = configuration.getMappedStatement(
                FileVersionMapper.class.getName()
                        + ".queryLatestByDefaultTitleForUpdate");

        String sql = statement.getBoundSql(Map.of(
                        "tenantId", 1,
                        "repositoryId", 1,
                        "defaultTitle", "draft-123"))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();

        assertTrue(sql.contains("ORDER BY VERSION_NO DESC"));
        assertTrue(sql.endsWith("FOR UPDATE"));
    }

    @Test
    void ordinaryFileQueriesExcludeSoftDeletedRows() throws Exception {
        Configuration configuration = loadMapper("mapper/FileMapper.xml");

        for (String method : List.of(
                "queryById",
                "queryByIdForUpdate",
                "queryByRepositoryId",
                "queryByTitleAndRepository",
                "queryByTenantIdRepositoryIdAndTitle")) {
            MappedStatement statement = configuration.getMappedStatement(
                    FileMapper.class.getName() + "." + method);
            String sql = statement.getBoundSql(Map.of(
                            "id", 42,
                            "tenantId", 1,
                            "repositoryId", 1,
                            "title", "document"))
                    .getSql()
                    .replaceAll("\\s+", " ")
                    .toUpperCase();

            assertTrue(
                    sql.contains("IS_DELETED = 0"),
                    method + " must exclude files in trash");
        }
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

    private static void assertEveryMethodIsBound(
            Configuration configuration,
            Class<?> mapperType) {
        List<String> missing = new ArrayList<>();
        for (Method method : mapperType.getDeclaredMethods()) {
            String statementId = mapperType.getName() + "." + method.getName();
            if (!configuration.hasStatement(statementId)) {
                missing.add(method.getName());
            }
        }

        assertTrue(
                missing.isEmpty(),
                mapperType.getSimpleName()
                        + " methods without matching XML statements: "
                        + missing);
    }
}
