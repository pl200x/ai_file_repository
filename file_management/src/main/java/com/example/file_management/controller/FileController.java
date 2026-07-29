package com.example.file_management.controller;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.controller.dto.DeleteFileDTO;
import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.dto.UpdateFileDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.FileTitleConflictException;
import com.example.file_management.exception.InvalidFileVersionException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    @Autowired
    private FileService fileService;

    @PostMapping("/addfile")
    public DataVO<FileWriteResultVO> addFile(@RequestBody AddFileDTO addFileDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            //首个版本由创建请求生成：editor即owner；fileId/versionNo在service里查库确定；
            //defaultTitle回传草稿阶段前端生成的idempotency key，让v1接上草稿期已有的版本链
            FileVersionDTO fileVersionDTO = new FileVersionDTO(null, null, 0,
                    addFileDTO.title(), addFileDTO.content(), addFileDTO.ownerId(),
                    null, null, addFileDTO.repositoryId(), addFileDTO.tenantId(),
                    addFileDTO.defaultTitle());
            FileWriteResultVO result = fileService.addFile(addFileDTO, fileVersionDTO);
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
        } catch (FileTitleConflictException | VersionChainConflictException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(409, end - start, false, e.getMessage(), null);
        } catch (InvalidFileVersionException | IllegalArgumentException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(400, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to add file", e);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @PostMapping("/updatefile")
    public DataVO<FileWriteResultVO> updateFile(@RequestBody UpdateFileDTO updateFileDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            //提交即快照版本：editor即本次提交人；tenant/repo在service里以file行为准重建；
            //defaultTitle原样透传updateFileDTO里的值(前端从该文件版本历史读出后回传)
            FileVersionDTO fileVersionDTO = new FileVersionDTO(null, updateFileDTO.id(), 0,
                    updateFileDTO.title(), updateFileDTO.content(), updateFileDTO.latestModifiedUserId(),
                    null, null, 0, 0, updateFileDTO.defaultTitle());
            FileWriteResultVO result = fileService.updateFile(updateFileDTO, fileVersionDTO);
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
        } catch (FileTitleConflictException | VersionChainConflictException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(409, end - start, false, e.getMessage(), null);
        } catch (InvalidFileVersionException | IllegalArgumentException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(400, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to update file", e);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @GetMapping("/query")
    public DataVO<File> queryById(@RequestParam int id, @RequestParam int userId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            File file = fileService.queryById(id, userId);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, file);
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

    @GetMapping("/list")
    public DataVO<List<File>> queryByRepositoryId(@RequestParam int tenantId, @RequestParam int repositoryId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            List<File> files = fileService.queryByRepositoryId(tenantId, repositoryId);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, files);
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @DeleteMapping("/delete_file")
    public DataVO<Void> deleteFileByFileId(
            @RequestParam int id,
            @RequestParam int latestModifiedUserId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            fileService.deleteByFileId(
                    new DeleteFileDTO(id, latestModifiedUserId));
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, null);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(404, end - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(501, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to permanently delete file {}", id, e);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @PostMapping("/move_to_trash_bin")
    public DataVO<Void> moveToTrashBin(
            @RequestParam int id,
            @RequestParam int latestModifiedUserId) {
        long start = System.currentTimeMillis();
        try {
            fileService.moveToTrashBin(
                    new DeleteFileDTO(id, latestModifiedUserId));
            return DataVO.buildDataVO(
                    200, System.currentTimeMillis() - start, true, null, null);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    404, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    501, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to move file {} to trash", id, e);
            return DataVO.buildDataVO(
                    500, System.currentTimeMillis() - start, false, "other unknown error", null);
        }
    }

    @PostMapping("/restore_from_trash_bin")
    public DataVO<Void> restoreFromTrashBin(
            @RequestParam int id,
            @RequestParam int latestModifiedUserId) {
        long start = System.currentTimeMillis();
        try {
            fileService.restoreFromTrashBin(
                    new DeleteFileDTO(id, latestModifiedUserId));
            return DataVO.buildDataVO(
                    200, System.currentTimeMillis() - start, true, null, null);
        } catch (CantFindTargetFileException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    404, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (UserPermissionDeniedException e) {
            logger.error(e.toString());
            return DataVO.buildDataVO(
                    501, System.currentTimeMillis() - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("failed to restore file {} from trash", id, e);
            return DataVO.buildDataVO(
                    500, System.currentTimeMillis() - start, false, "other unknown error", null);
        }
    }

    @GetMapping("/trash_bin")
    public DataVO<List<File>> queryAllTrashBin(@RequestParam int tenantId) {
        long start = System.currentTimeMillis();
        try {
            List<File> files = fileService.queryAllTrashBin(tenantId);
            return DataVO.buildDataVO(
                    200, System.currentTimeMillis() - start, true, null, files);
        } catch (Exception e) {
            logger.error("failed to query trash bin for tenant {}", tenantId, e);
            return DataVO.buildDataVO(
                    500, System.currentTimeMillis() - start, false, "other unknown error", null);
        }
    }
}
