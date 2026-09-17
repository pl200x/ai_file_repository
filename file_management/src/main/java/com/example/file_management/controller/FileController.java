package com.example.file_management.controller;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.controller.dto.DeleteFileDTO;
import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.dto.UpdateFileDTO;
import com.example.file_management.controller.vo.DataVO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.FileTitleConflictException;
import com.example.file_management.exception.InvalidFileVersionException;
import com.example.file_management.exception.PdfHasNoExtractableTextException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.service.FileService;
import com.example.file_management.service.MarkdownExtractionService;
import com.example.file_management.service.PdfExportService;
import com.example.file_management.service.PdfExtractionService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    @Autowired
    private FileService fileService;
    @Autowired
    private PdfExtractionService pdfExtractionService;
    @Autowired
    private MarkdownExtractionService markdownExtractionService;
    @Autowired
    private PdfExportService pdfExportService;

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

    @PostMapping(value = "/upload_pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DataVO<FileWriteResultVO> uploadPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam int repositoryId,
            @RequestParam int ownerId,
            @RequestParam int tenantId,
            @RequestParam String defaultTitle,
            @RequestParam(required = false) String title) {
        long start = System.currentTimeMillis();
        long end;
        try {
            String extractedText = pdfExtractionService.extractText(file);
            boolean autoTitle = title == null || title.isBlank();
            //复用既有 addFile：权限校验、标题去重、建版本、异步切分全部不变，
            //Tika 提取出的文本只是 content 的另一种来源
            AddFileDTO addFileDTO = new AddFileDTO(
                    repositoryId, ownerId,
                    autoTitle ? "" : title, extractedText, File.CONTENT_FORMAT_PLAIN,
                    null, null, null, null,
                    ownerId, tenantId, false,
                    defaultTitle, autoTitle);
            FileVersionDTO fileVersionDTO = new FileVersionDTO(null, null, 0,
                    addFileDTO.title(), addFileDTO.content(), addFileDTO.ownerId(),
                    null, null, addFileDTO.repositoryId(), addFileDTO.tenantId(),
                    addFileDTO.defaultTitle());
            FileWriteResultVO result = fileService.addFile(addFileDTO, fileVersionDTO);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, result);
        } catch (PdfHasNoExtractableTextException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(422, end - start, false, e.getMessage(), null);
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
            logger.error("failed to upload pdf", e);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }

    @PostMapping(value = "/upload_markdown", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DataVO<FileWriteResultVO> uploadMarkdown(
            @RequestPart("file") MultipartFile file,
            @RequestParam int repositoryId,
            @RequestParam int ownerId,
            @RequestParam int tenantId,
            @RequestParam String defaultTitle,
            @RequestParam(required = false) String title) {
        long start = System.currentTimeMillis();
        long end;
        try {
            String extractedText = markdownExtractionService.extractText(file);
            boolean autoTitle = title == null || title.isBlank();
            //和uploadPdf同一套addFile管线，唯一区别是contentFormat标成MARKDOWN，
            //供前端决定按渲染模式还是编辑模式展示正文
            AddFileDTO addFileDTO = new AddFileDTO(
                    repositoryId, ownerId,
                    autoTitle ? "" : title, extractedText, File.CONTENT_FORMAT_MARKDOWN,
                    null, null, null, null,
                    ownerId, tenantId, false,
                    defaultTitle, autoTitle);
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
            logger.error("failed to upload markdown", e);
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

    @GetMapping("/{id}/export_pdf")
    public void exportPdf(
            @PathVariable int id,
            @RequestParam int userId,
            HttpServletResponse response) throws IOException {
        File file;
        try {
            //复用既有 READABLE 权限校验；追加 readerList 的副作用与正常阅读一致，符合预期
            file = fileService.queryById(id, userId);
        } catch (CantFindTargetFileException e) {
            logger.warn(e.toString());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        } catch (UserPermissionDeniedException e) {
            logger.warn(e.toString());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
        }

        byte[] pdfBytes;
        try {
            pdfBytes = pdfExportService.export(file.getTitle(), file.getContent());
        } catch (Exception e) {
            logger.error("failed to export file {} as pdf", id, e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "failed to render PDF");
            return;
        }

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodeFilename(file.getTitle()) + ".pdf\"");
        response.setContentLength(pdfBytes.length);
        response.getOutputStream().write(pdfBytes);
    }

    private String encodeFilename(String title) {
        String safeTitle = (title == null || title.isBlank()) ? "document" : title;
        return URLEncoder.encode(safeTitle, StandardCharsets.UTF_8).replace("+", "%20");
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
