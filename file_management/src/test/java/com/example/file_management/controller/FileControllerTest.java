package com.example.file_management.controller;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.dto.UpdateFileDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;
import com.example.file_management.exception.FileTitleConflictException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.service.FileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
class FileControllerTest {

    private static final String TEXT_ONLY_CONTENT = "First paragraph\n\nSecond paragraph";
    private static final String SINGLE_IMAGE_CONTENT =
            "First paragraph\n\n[[IMAGE:data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==]]\n\nText after image";
    private static final String MULTIPLE_IMAGE_CONTENT =
            "Before\n[[IMAGE:data:image/jpeg;base64,/9j/4AAQSkZJRg==]]\n"
                    + "Between\n[[IMAGE:data:image/webp;base64,UklGRiIAAABXRUJQVlA=]]\nAfter";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    void addFile_textOnlyContent_passesContentUnchangedToCreateAndVersionWorkflows() throws Exception {
        when(fileService.addFile(any(AddFileDTO.class), any(FileVersionDTO.class)))
                .thenReturn(new FileWriteResultVO(42, 4, "draft-123", "design doc"));

        mockMvc.perform(post("/api/file/addfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(TEXT_ONLY_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errorMessage").isEmpty())
                .andExpect(jsonPath("$.data.fileId").value(42))
                .andExpect(jsonPath("$.data.versionNo").value(4))
                .andExpect(jsonPath("$.data.defaultTitle").value("draft-123"))
                .andExpect(jsonPath("$.data.title").value("design doc"));

        ArgumentCaptor<AddFileDTO> fileCaptor = ArgumentCaptor.forClass(AddFileDTO.class);
        ArgumentCaptor<FileVersionDTO> versionCaptor = ArgumentCaptor.forClass(FileVersionDTO.class);
        verify(fileService).addFile(fileCaptor.capture(), versionCaptor.capture());
        assertEquals(TEXT_ONLY_CONTENT, fileCaptor.getValue().content());
        assertEquals(TEXT_ONLY_CONTENT, versionCaptor.getValue().content());
        assertEquals("draft-123", versionCaptor.getValue().defaultTitle());
        assertEquals(false, fileCaptor.getValue().autoTitle());
    }

    @Test
    void addFile_singleImageContent_passesMarkerUnchangedToCreateAndVersionWorkflows() throws Exception {
        when(fileService.addFile(any(AddFileDTO.class), any(FileVersionDTO.class)))
                .thenReturn(new FileWriteResultVO(42, 4, "draft-123", "design doc"));

        mockMvc.perform(post("/api/file/addfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(SINGLE_IMAGE_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<AddFileDTO> fileCaptor = ArgumentCaptor.forClass(AddFileDTO.class);
        ArgumentCaptor<FileVersionDTO> versionCaptor = ArgumentCaptor.forClass(FileVersionDTO.class);
        verify(fileService).addFile(fileCaptor.capture(), versionCaptor.capture());
        assertEquals(SINGLE_IMAGE_CONTENT, fileCaptor.getValue().content());
        assertEquals(SINGLE_IMAGE_CONTENT, versionCaptor.getValue().content());
    }

    @Test
    void updateFile_existingImages_passesOrderedContentUnchangedToUpdateAndVersionWorkflows() throws Exception {
        when(fileService.updateFile(any(UpdateFileDTO.class), any(FileVersionDTO.class)))
                .thenReturn(new FileWriteResultVO(
                        42, 5, "draft-123", "updated design doc"));

        mockMvc.perform(post("/api/file/updatefile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(MULTIPLE_IMAGE_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value(42))
                .andExpect(jsonPath("$.data.versionNo").value(5));

        ArgumentCaptor<UpdateFileDTO> fileCaptor = ArgumentCaptor.forClass(UpdateFileDTO.class);
        ArgumentCaptor<FileVersionDTO> versionCaptor = ArgumentCaptor.forClass(FileVersionDTO.class);
        verify(fileService).updateFile(fileCaptor.capture(), versionCaptor.capture());
        assertEquals(MULTIPLE_IMAGE_CONTENT, fileCaptor.getValue().content());
        assertEquals(MULTIPLE_IMAGE_CONTENT, versionCaptor.getValue().content());
        assertEquals("draft-123", versionCaptor.getValue().defaultTitle());
        assertEquals(false, fileCaptor.getValue().autoTitle());
    }

    @Test
    void addFile_permissionDenied_returnsBodyCode501ButHttp200() throws Exception {
        doThrow(new UserPermissionDeniedException("you don't have access"))
                .when(fileService).addFile(any(AddFileDTO.class), any(FileVersionDTO.class));

        mockMvc.perform(post("/api/file/addfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(TEXT_ONLY_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("you don't have access"));
    }

    @Test
    void addFile_unknownError_swallowsRealErrorMessage() throws Exception {
        doThrow(new IllegalStateException("database connection refused"))
                .when(fileService).addFile(any(AddFileDTO.class), any(FileVersionDTO.class));

        mockMvc.perform(post("/api/file/addfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(TEXT_ONLY_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("other unknown error"));
    }

    @Test
    void addFile_titleConflict_returnsBusinessCode409() throws Exception {
        doThrow(new FileTitleConflictException("duplicate title"))
                .when(fileService).addFile(
                        any(AddFileDTO.class), any(FileVersionDTO.class));

        mockMvc.perform(post("/api/file/addfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(TEXT_ONLY_CONTENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("duplicate title"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void moveToTrashBin_passesFileAndCurrentUserToService() throws Exception {
        mockMvc.perform(post("/api/file/move_to_trash_bin")
                        .param("id", "42")
                        .param("latestModifiedUserId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        verify(fileService).moveToTrashBin(
                new com.example.file_management.controller.dto.DeleteFileDTO(
                        42, 10));
    }

    @Test
    void deleteFileByFileId_passesFileAndCurrentUserToService() throws Exception {
        mockMvc.perform(delete("/api/file/delete_file")
                        .param("id", "42")
                        .param("latestModifiedUserId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        verify(fileService).deleteByFileId(
                new com.example.file_management.controller.dto.DeleteFileDTO(
                        42, 10));
    }

    @Test
    void restoreFromTrashBin_passesFileAndCurrentUserToService() throws Exception {
        mockMvc.perform(post("/api/file/restore_from_trash_bin")
                        .param("id", "42")
                        .param("latestModifiedUserId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true));

        verify(fileService).restoreFromTrashBin(
                new com.example.file_management.controller.dto.DeleteFileDTO(
                        42, 10));
    }

    @Test
    void queryAllTrashBin_returnsDeletedDocuments() throws Exception {
        File deleted = new File();
        deleted.setId(42);
        deleted.setDeleted(true);
        when(fileService.queryAllTrashBin(1)).thenReturn(java.util.List.of(deleted));

        mockMvc.perform(get("/api/file/trash_bin").param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(42))
                .andExpect(jsonPath("$.data[0].deleted").value(true));
    }

    private static String addBody(String content) {
        return """
                {
                  "repositoryId": 1,
                  "ownerId": 10,
                  "title": "design doc",
                  "content": %s,
                  "writableList": "10,11",
                  "readableList": "10",
                  "manageableList": "10",
                  "latestModifiedUserId": 10,
                  "tenantId": 1,
                  "isPrivate": false,
                  "defaultTitle": "draft-123",
                  "autoTitle": false
                }
                """.formatted(asJsonString(content));
    }

    private static String updateBody(String content) {
        return """
                {
                  "id": 42,
                  "title": "updated design doc",
                  "content": %s,
                  "writableList": "10,11",
                  "readableList": "10",
                  "manageableList": "10",
                  "latestModifiedUserId": 10,
                  "isPrivate": false,
                  "defaultTitle": "draft-123",
                  "autoTitle": false
                }
                """.formatted(asJsonString(content));
    }

    private static String asJsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                + "\"";
    }
}
