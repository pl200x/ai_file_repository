package com.example.file_management.mapper;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.entity.File;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileMapper {
    void addFile(File file);
    int updateFile(File file);
    void updateReaderList(@Param("id") int id, @Param("readerList") String readerList);
    File queryById(int id);
    File queryByIdForUpdate(int id);
    List<File> queryByRepositoryId(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId
    );
    File queryByTitleAndRepository(String title, int repositoryId);
    File queryByTenantIdRepositoryIdAndTitle(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId,
            @Param("title") String title
    );
    File queryDeletedById(int id);
    List<File> queryAllTrashBin(@Param("tenantId") int tenantId);
    int moveToTrashBin(
            @Param("id") int id,
            @Param("latestModifiedUserId") int latestModifiedUserId
    );
    int restoreFromTrashBin(
            @Param("id") int id,
            @Param("latestModifiedUserId") int latestModifiedUserId
    );
    int deleteByFileId(int id);
}
