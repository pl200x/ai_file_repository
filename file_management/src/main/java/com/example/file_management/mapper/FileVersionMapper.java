package com.example.file_management.mapper;

import com.example.file_management.entity.FileVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileVersionMapper {
    void insertVersion(FileVersion fileVersion);

    FileVersion queryLatestByFileId(int fileId);

    List<FileVersion> queryByFileId(int fileId);

    FileVersion queryByFileIdAndVersionNo(
            @Param("fileId") int fileId,
            @Param("versionNo") int versionNo
    );

    FileVersion queryLastByTenantIdRepositoryIdAndTitle(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId,
            @Param("defaultTitle") String defaultTitle
    );

    FileVersion queryLatestByDefaultTitleForUpdate(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId,
            @Param("defaultTitle") String defaultTitle
    );

    List<Integer> queryLinkedFileIdsByDefaultTitle(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId,
            @Param("defaultTitle") String defaultTitle
    );

    List<FileVersion> queryByDefaultTitle(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId,
            @Param("defaultTitle") String defaultTitle
    );

    //文档刚创建时，把同一defaultTitle下还挂着file_id=NULL的草稿版本行过继给新file_id，
    //让版本号在创建前后连续，之后按file_id查版本历史也能看到草稿期的自动保存记录
    int adoptDraftVersions(
            @Param("tenantId") int tenantId,
            @Param("repositoryId") int repositoryId,
            @Param("defaultTitle") String defaultTitle,
            @Param("fileId") int fileId
    );
}
