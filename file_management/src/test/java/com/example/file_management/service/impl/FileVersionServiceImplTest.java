package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;
import com.example.file_management.entity.FileVersion;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.mapper.FileVersionMapper;
import com.example.file_management.mapper.KnowledgeRepositoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVersionServiceImplTest {

    private static final String DEFAULT_TITLE = "draft-123";
    private static final String TEXT_ONLY_CONTENT =
            "First paragraph\n\nSecond paragraph";
    private static final String SINGLE_IMAGE_CONTENT =
            "First paragraph\n[[IMAGE:data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==]]\nText after image";
    private static final String MULTIPLE_IMAGE_CONTENT =
            "Before\n[[IMAGE:data:image/jpeg;base64,/9j/4AAQSkZJRg==]]\n"
                    + "Between\n[[IMAGE:data:image/webp;base64,UklGRiIAAABXRUJQVlA=]]\nAfter";

    @Mock
    private FileVersionMapper fileVersionMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private KnowledgeRepositoryMapper knowledgeRepositoryMapper;
    @Mock
    private PermissionIntegration permissionIntegration;

    @InjectMocks
    private FileVersionServiceImpl fileVersionService;

    void allowDraftWrites() {
        KnowledgeRepository repository = new KnowledgeRepository();
        repository.setId(1L);
        repository.setTenantId(1);
        when(knowledgeRepositoryMapper.queryById(1)).thenReturn(repository);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                anyString(), anyInt(), anyInt(), anyString())).thenReturn(true);
    }

    @Test
    void initializeFileVersion_emptyDocument_insertsEmptyV1() {
        allowDraftWrites();
        stubRepositoryLock();

        FileWriteResultVO result = fileVersionService.initializeFileVersion(
                dto(null, null, 0, "", "", DEFAULT_TITLE));

        FileVersion saved = captureInsertedVersion();
        assertNull(saved.getFileId());
        assertEquals(1, saved.getVersionNo());
        assertEquals("", saved.getTitle());
        assertEquals("", saved.getContent());
        assertEquals(DEFAULT_TITLE, saved.getDefaultTitle());
        assertNull(result.fileId());
        assertEquals(1, result.versionNo());
        assertEquals(DEFAULT_TITLE, result.defaultTitle());
        assertEquals("", result.title());
    }

    @Test
    void initializeFileVersion_sameKeyTwice_returnsExistingV1WithoutAnotherInsert() {
        allowDraftWrites();
        FileVersion initial = version(null, 1, "", "", DEFAULT_TITLE);
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(initial);

        FileWriteResultVO result = fileVersionService.initializeFileVersion(
                dto(null, null, 0, "", "", DEFAULT_TITLE));

        verify(fileVersionMapper, never()).insertVersion(org.mockito.ArgumentMatchers.any());
        verify(knowledgeRepositoryMapper, never()).queryByIdForUpdate(anyInt());
        assertNull(result.fileId());
        assertEquals(1, result.versionNo());
        assertEquals(DEFAULT_TITLE, result.defaultTitle());
    }

    @Test
    void addFileVersion_blankTitle_appendsAutosaveSnapshot() {
        allowDraftWrites();
        FileVersion initial = version(null, 1, "", "", DEFAULT_TITLE);
        stubLatestDraft(initial);

        FileWriteResultVO result = fileVersionService.addFileVersion(
                dto(null, null, 1, "", TEXT_ONLY_CONTENT, DEFAULT_TITLE));

        FileVersion saved = captureInsertedVersion();
        assertNull(saved.getFileId());
        assertEquals(2, saved.getVersionNo());
        assertEquals("", saved.getTitle());
        assertEquals(TEXT_ONLY_CONTENT, saved.getContent());
        assertEquals(DEFAULT_TITLE, saved.getDefaultTitle());
        assertEquals(2, result.versionNo());
    }

    @Test
    void addFileVersion_changedDisplayTitle_staysOnDefaultTitleChain() {
        allowDraftWrites();
        FileVersion latest = version(
                null, 2, "Old draft title", SINGLE_IMAGE_CONTENT, DEFAULT_TITLE);
        stubLatestDraft(latest);

        fileVersionService.addFileVersion(
                dto(null, null, 2, "Renamed draft", MULTIPLE_IMAGE_CONTENT, DEFAULT_TITLE));

        FileVersion saved = captureInsertedVersion();
        assertEquals(3, saved.getVersionNo());
        assertEquals("Renamed draft", saved.getTitle());
        assertEquals(DEFAULT_TITLE, saved.getDefaultTitle());
        assertEquals(MULTIPLE_IMAGE_CONTENT, saved.getContent());
        verify(fileMapper, never()).queryByTenantIdRepositoryIdAndTitle(
                anyInt(), anyInt(), anyString());
    }

    @Test
    void addFileVersion_firstCommittedSnapshot_adoptsEveryDraftByKey() {
        FileVersion latestDraft =
                version(null, 3, "Draft title", TEXT_ONLY_CONTENT, DEFAULT_TITLE);
        stubLatestDraft(latestDraft);

        File linkedFile = existingFile(42);
        when(fileMapper.queryById(42)).thenReturn(linkedFile);

        FileWriteResultVO result = fileVersionService.addFileVersion(
                dto(null, 42, 3, "Committed title", MULTIPLE_IMAGE_CONTENT, DEFAULT_TITLE));

        verify(fileVersionMapper).adoptDraftVersions(1, 1, DEFAULT_TITLE, 42);
        FileVersion saved = captureInsertedVersion();
        assertEquals(42, saved.getFileId());
        assertEquals(4, saved.getVersionNo());
        assertEquals("Committed title", saved.getTitle());
        assertEquals(MULTIPLE_IMAGE_CONTENT, saved.getContent());
        assertEquals(42, result.fileId());
        assertEquals(4, result.versionNo());
    }

    @Test
    void addFileVersion_linkedChainUsesFileIdEvenWhenTitleChanges() {
        FileVersion latest =
                version(42, 4, "Old title", TEXT_ONLY_CONTENT, DEFAULT_TITLE);
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLatestByDefaultTitleForUpdate(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(List.of(42));
        when(fileMapper.queryById(42)).thenReturn(existingFile(42));

        fileVersionService.addFileVersion(
                dto(null, 42, 4, "New title", SINGLE_IMAGE_CONTENT, DEFAULT_TITLE));

        FileVersion saved = captureInsertedVersion();
        assertEquals(42, saved.getFileId());
        assertEquals(5, saved.getVersionNo());
        assertEquals("New title", saved.getTitle());
        verify(fileVersionMapper, never()).adoptDraftVersions(
                anyInt(), anyInt(), anyString(), anyInt());
    }

    @Test
    void addFileVersion_staleExpectedVersion_rejectsWithoutInsert() {
        allowDraftWrites();
        FileVersion latest =
                version(null, 3, "Draft", TEXT_ONLY_CONTENT, DEFAULT_TITLE);
        stubLatestDraft(latest);

        VersionChainConflictException error = assertThrows(
                VersionChainConflictException.class,
                () -> fileVersionService.addFileVersion(
                        dto(null, null, 2, "Draft", "stale content", DEFAULT_TITLE)));

        assertTrue(error.getMessage().contains("expected v2"));
        verify(fileVersionMapper, never()).insertVersion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addFileVersion_conflictingRequestedFile_rejectsWithoutAdoptionOrInsert() {
        FileVersion latest =
                version(42, 3, "Draft", TEXT_ONLY_CONTENT, DEFAULT_TITLE);
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLatestByDefaultTitleForUpdate(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(List.of(42));

        assertThrows(
                VersionChainConflictException.class,
                () -> fileVersionService.addFileVersion(
                        dto(null, 43, 3, "Draft", TEXT_ONLY_CONTENT, DEFAULT_TITLE)));

        verify(fileVersionMapper, never()).adoptDraftVersions(
                anyInt(), anyInt(), anyString(), anyInt());
        verify(fileVersionMapper, never()).insertVersion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addFileVersion_chainLinkedToMultipleFiles_rejectsWithoutInsert() {
        FileVersion latest =
                version(42, 3, "Draft", TEXT_ONLY_CONTENT, DEFAULT_TITLE);
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLatestByDefaultTitleForUpdate(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(List.of(42, 43));

        assertThrows(
                VersionChainConflictException.class,
                () -> fileVersionService.addFileVersion(
                        dto(null, 42, 3, "Draft", TEXT_ONLY_CONTENT, DEFAULT_TITLE)));

        verify(fileVersionMapper, never()).insertVersion(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void initializeAndAppendAreTransactionalRollbackBoundaries() throws Exception {
        assertTrue(FileVersionServiceImpl.class
                .getMethod("initializeFileVersion", FileVersionDTO.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(FileVersionServiceImpl.class
                .getMethod("addFileVersion", FileVersionDTO.class)
                .isAnnotationPresent(Transactional.class));
    }

    private void stubRepositoryLock() {
        KnowledgeRepository repository = new KnowledgeRepository();
        repository.setId(1L);
        repository.setTenantId(1);
        when(knowledgeRepositoryMapper.queryByIdForUpdate(1)).thenReturn(repository);
    }

    private void stubLatestDraft(FileVersion latest) {
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
        when(fileVersionMapper.queryLatestByDefaultTitleForUpdate(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);
    }

    private FileVersion captureInsertedVersion() {
        ArgumentCaptor<FileVersion> captor = ArgumentCaptor.forClass(FileVersion.class);
        verify(fileVersionMapper).insertVersion(captor.capture());
        return captor.getValue();
    }

    private static FileVersionDTO dto(
            Long id,
            Integer fileId,
            int versionNo,
            String title,
            String content,
            String defaultTitle) {
        return new FileVersionDTO(
                id, fileId, versionNo, title, content,
                10, null, null, 1, 1, defaultTitle);
    }

    private static FileVersion version(
            Integer fileId,
            int versionNo,
            String title,
            String content,
            String defaultTitle) {
        FileVersion version = new FileVersion();
        version.setFileId(fileId);
        version.setVersionNo(versionNo);
        version.setTitle(title);
        version.setContent(content);
        version.setEditorId(10);
        version.setRepositoryId(1);
        version.setTenantId(1);
        version.setDefaultTitle(defaultTitle);
        return version;
    }

    private static File existingFile(int id) {
        File file = new File();
        file.setId(id);
        file.setOwnerId(10);
        file.setRepositoryId(1);
        file.setTenantId(1);
        file.setTitle("design doc");
        return file;
    }
}
