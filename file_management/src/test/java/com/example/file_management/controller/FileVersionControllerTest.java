package com.example.file_management.controller;

import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.exception.InvalidFileVersionException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.service.FileVersionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileVersionController.class)
class FileVersionControllerTest {

    private static final String DEFAULT_TITLE = "draft-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileVersionService fileVersionService;

    @Test
    void initializeFileVersion_ignoresClientFileAndVersionIdsAndReturnsV1() throws Exception {
        when(fileVersionService.initializeFileVersion(any()))
                .thenReturn(new FileWriteResultVO(null, 1, DEFAULT_TITLE, ""));

        mockMvc.perform(post("/api/file/initialize_file_version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(999, 88, "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").doesNotExist())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.defaultTitle").value(DEFAULT_TITLE))
                .andExpect(jsonPath("$.data.title").value(""));

        ArgumentCaptor<FileVersionDTO> captor =
                ArgumentCaptor.forClass(FileVersionDTO.class);
        verify(fileVersionService).initializeFileVersion(captor.capture());
        assertNull(captor.getValue().fileId());
        assertEquals(0, captor.getValue().versionNo());
        assertEquals("", captor.getValue().title());
        assertEquals("", captor.getValue().content());
    }

    @Test
    void addFileVersion_blankTitle_keepsContentAndCannotAttachArbitraryFile() throws Exception {
        when(fileVersionService.addFileVersion(any()))
                .thenReturn(new FileWriteResultVO(null, 2, DEFAULT_TITLE, ""));
        String content = "First paragraph\nSecond paragraph";

        mockMvc.perform(post("/api/file/add_file_version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(999, 1, "", content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.versionNo").value(2));

        ArgumentCaptor<FileVersionDTO> captor =
                ArgumentCaptor.forClass(FileVersionDTO.class);
        verify(fileVersionService).addFileVersion(captor.capture());
        assertNull(captor.getValue().fileId());
        assertEquals(1, captor.getValue().versionNo());
        assertEquals("", captor.getValue().title());
        assertEquals(content, captor.getValue().content());
        assertEquals(DEFAULT_TITLE, captor.getValue().defaultTitle());
    }

    @Test
    void addFileVersion_staleVersionConflict_returns409() throws Exception {
        doThrow(new VersionChainConflictException("stale version"))
                .when(fileVersionService).addFileVersion(any());

        mockMvc.perform(post("/api/file/add_file_version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(null, 2, "", "content")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("stale version"));
    }

    @Test
    void initializeFileVersion_invalidKey_returns400() throws Exception {
        doThrow(new InvalidFileVersionException("defaultTitle is required"))
                .when(fileVersionService).initializeFileVersion(any());

        mockMvc.perform(post("/api/file/initialize_file_version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(null, 0, "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage")
                        .value("defaultTitle is required"));
    }

    @Test
    void addFileVersion_permissionDenied_returns501() throws Exception {
        doThrow(new UserPermissionDeniedException("write denied"))
                .when(fileVersionService).addFileVersion(any());

        mockMvc.perform(post("/api/file/add_file_version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody(null, 1, "", "content")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorMessage").value("write denied"));
    }

    private static String versionBody(
            Integer fileId,
            int versionNo,
            String title,
            String content) {
        String fileIdJson = fileId == null ? "null" : fileId.toString();
        return """
                {
                  "id": null,
                  "fileId": %s,
                  "versionNo": %d,
                  "title": %s,
                  "content": %s,
                  "editorId": 10,
                  "openTime": null,
                  "lastMergeTime": null,
                  "repositoryId": 1,
                  "tenantId": 1,
                  "defaultTitle": "%s"
                }
                """.formatted(
                fileIdJson,
                versionNo,
                asJsonString(title),
                asJsonString(content),
                DEFAULT_TITLE);
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
