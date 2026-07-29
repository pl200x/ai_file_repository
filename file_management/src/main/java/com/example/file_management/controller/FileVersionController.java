package com.example.file_management.controller;

import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.FileVersion;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.InvalidFileVersionException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.service.FileVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileVersionController {
    private static final Logger logger = LoggerFactory.getLogger(FileVersionController.class);
    @Autowired
    private FileVersionService fileVersionService;

    @PostMapping("/add_file_version")
    public DataVO<FileWriteResultVO> addFileVersion(@RequestBody FileVersionDTO fileVersionDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            // The public autosave endpoint cannot attach an unlinked draft chain
            // to an arbitrary file id. Existing files are resolved by defaultTitle.
            FileVersionDTO safeDTO = new FileVersionDTO(
                    fileVersionDTO.id(), null, fileVersionDTO.versionNo(),
                    fileVersionDTO.title(), fileVersionDTO.content(), fileVersionDTO.editorId(),
                    fileVersionDTO.openTime(), fileVersionDTO.lastMergeTime(),
                    fileVersionDTO.repositoryId(), fileVersionDTO.tenantId(),
                    fileVersionDTO.defaultTitle());
            FileWriteResultVO result = fileVersionService.addFileVersion(safeDTO);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, result);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(404, end - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(501, end - start, false, e.getMessage(), null);
        } catch (VersionChainConflictException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(409, end - start, false, e.getMessage(), null);
        } catch (InvalidFileVersionException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(400, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to append file version", e);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @PostMapping("/initialize_file_version")
    public DataVO<FileWriteResultVO> initializeFileVersion(@RequestBody FileVersionDTO fileVersionDTO) {
        long start = System.currentTimeMillis();
        try {
            FileVersionDTO safeDTO = new FileVersionDTO(
                    fileVersionDTO.id(), null, 0,
                    fileVersionDTO.title(), fileVersionDTO.content(), fileVersionDTO.editorId(),
                    fileVersionDTO.openTime(), fileVersionDTO.lastMergeTime(),
                    fileVersionDTO.repositoryId(), fileVersionDTO.tenantId(),
                    fileVersionDTO.defaultTitle());
            FileWriteResultVO result = fileVersionService.initializeFileVersion(safeDTO);
            return DataVO.buildDataVO(
                    200, System.currentTimeMillis() - start, true, null, result);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    404, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    501, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (VersionChainConflictException e) {
            logger.warn(e.toString());
            return DataVO.buildDataVO(
                    409, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (InvalidFileVersionException e) {
            logger.warn(e.toString());
            return DataVO.buildDataVO(
                    400, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to initialize file version", e);
            return DataVO.buildDataVO(
                    500, System.currentTimeMillis() - start, false, "other unknown error", null);
        }
    }

    @GetMapping("/versions")
    public DataVO<List<FileVersion>> queryVersionsByFileId(@RequestParam int fileId, @RequestParam int userId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            List<FileVersion> versions = fileVersionService.queryVersionsByFileId(fileId, userId);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, versions);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(404, end - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(501, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @GetMapping("/version_detail")
    public DataVO<FileVersion> queryVersionDetail(
            @RequestParam int fileId,
            @RequestParam int versionNo,
            @RequestParam int userId) {
        long start = System.currentTimeMillis();
        try {
            FileVersion version = fileVersionService.queryVersionDetail(fileId, versionNo, userId);
            return DataVO.buildDataVO(
                    200, System.currentTimeMillis() - start, true, null, version);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    404, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    501, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to query file version detail", e);
            return DataVO.buildDataVO(
                    500, System.currentTimeMillis() - start, false, "other unknown error", null);
        }
    }

    //文档尚未创建(新建草稿阶段)时查版本历史：靠defaultTitle定位，不受草稿期改标题影响
    @GetMapping("/versions_by_default_title")
    public DataVO<List<FileVersion>> queryVersionsByDefaultTitle(
            @RequestParam String defaultTitle,
            @RequestParam int repositoryId,
            @RequestParam int tenantId,
            @RequestParam int userId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            List<FileVersion> versions = fileVersionService.queryVersionsByDefaultTitle(
                    defaultTitle, repositoryId, tenantId, userId);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, versions);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(404, end - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(501, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to query draft versions", e);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }
}
