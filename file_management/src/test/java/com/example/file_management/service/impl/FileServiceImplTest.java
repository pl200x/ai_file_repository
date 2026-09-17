package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.controller.dto.DeleteFileDTO;
import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.dto.UpdateFileDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.Chunk;
import com.example.file_management.entity.ChunkSplittingMessage;
import com.example.file_management.entity.File;
import com.example.file_management.entity.FileVersion;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.integration.PermissionDTO;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.mapper.FileVersionMapper;
import com.example.file_management.mapper.KnowledgeRepositoryMapper;
import com.example.file_management.mapper.UserMapper;
import com.example.file_management.service.ChunkService;
import com.example.file_management.service.FileVersionService;
import com.example.file_management.service.producer.ChunkSplittingProducer;
import com.example.file_management.service.producer.PermissionBatchGivingProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    private static final String DEFAULT_TITLE = "draft-123";
    private static final String TEXT_ONLY_CONTENT =
            "First paragraph\n\nSecond paragraph";
    private static final String SINGLE_IMAGE_CONTENT =
            "First paragraph\n[[IMAGE:data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==]]\nText after image";
    private static final String IMAGE_ONLY_CONTENT =
            "[[IMAGE:data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==]]";
    private static final String MULTIPLE_IMAGE_CONTENT =
            "Before\n[[IMAGE:data:image/jpeg;base64,/9j/4AAQSkZJRg==]]\n"
                    + "Between\n[[IMAGE:data:image/gif;base64,R0lGODlhAQABAIAAAA==]]\nAfter";

    @Mock
    private FileMapper fileMapper;
    @Mock
    private FileVersionMapper fileVersionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PermissionIntegration permissionIntegration;
    @Mock
    private KnowledgeRepositoryMapper knowledgeRepositoryMapper;
    @Mock
    private PermissionBatchGivingProducer permissionBatchGivingProducer;
    @Mock
    private FileVersionService fileVersionService;
    @Mock
    private ChunkSplittingProducer chunkSplittingProducer;
    @Mock
    private ChunkService chunkService;

    @InjectMocks
    private FileServiceImpl fileService;

    @Test
    void addFile_noRepositoryPermission_throwsAndDoesNotInsert() {
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                anyString(), anyInt(), anyInt(), anyString())).thenReturn(false);

        assertThrows(
                UserPermissionDeniedException.class,
                () -> fileService.addFile(
                        addDto("design doc", TEXT_ONLY_CONTENT, false),
                        versionDto(null, "design doc", TEXT_ONLY_CONTENT)));

        verify(fileMapper, never()).addFile(any());
        verify(fileVersionService, never()).addFileVersion(any());
    }

    @Test
    void addFile_textOnlyContent_usesGeneratedIdAndAppendsCommittedSnapshot() {
        FileWriteResultVO result =
                executeSuccessfulAdd("design doc", TEXT_ONLY_CONTENT, false, 42);

        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).addFile(fileCaptor.capture());
        assertEquals(42, fileCaptor.getValue().getId());
        assertEquals("design doc", fileCaptor.getValue().getTitle());
        assertEquals(TEXT_ONLY_CONTENT, fileCaptor.getValue().getContent());

        FileVersionDTO committed = captureCommittedVersion();
        assertEquals(42, committed.fileId());
        assertEquals("design doc", committed.title());
        assertEquals(TEXT_ONLY_CONTENT, committed.content());
        assertEquals(DEFAULT_TITLE, committed.defaultTitle());
        assertEquals(42, result.fileId());
        verify(fileMapper, never()).queryByTitleAndRepository(anyString(), anyInt());
    }

    @Test
    void addFile_imageMarkers_preservesContentAndOrderingInCommittedSnapshot() {
        executeSuccessfulAdd("design doc", MULTIPLE_IMAGE_CONTENT, false, 42);

        FileVersionDTO committed = captureCommittedVersion();
        assertEquals(MULTIPLE_IMAGE_CONTENT, committed.content());
    }

    @Test
    void addFile_blankTitle_derivesFirstOrdinaryTextLine() {
        String content =
                "\n" + IMAGE_ONLY_CONTENT + "\n\n  First   useful\tline  \nSecond line";

        executeSuccessfulAdd("", content, true, 42);

        FileVersionDTO committed = captureCommittedVersion();
        assertEquals("First useful line", committed.title());
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).addFile(fileCaptor.capture());
        assertEquals("First useful line", fileCaptor.getValue().getTitle());
    }

    @Test
    void addFile_emptyContent_firstUntitledNameStartsAtOne() {
        executeSuccessfulAdd("未命名文档", "", true, 42);

        assertEquals("未命名文档(1)", captureCommittedVersion().title());
    }

    @Test
    void addFile_imageOnlyContent_firstUntitledNameStartsAtOne() {
        executeSuccessfulAdd("未命名文档", IMAGE_ONLY_CONTENT, true, 42);

        assertEquals("未命名文档(1)", captureCommittedVersion().title());
    }

    @Test
    void addFile_imageOnlyContent_skipsExistingUntitledNames() {
        stubAddPrerequisites(emptyRepository());
        File existingOne = existingFile(77);
        existingOne.setTitle("未命名文档(1)");
        when(fileMapper.queryByTenantIdRepositoryIdAndTitle(
                1, 1, "未命名文档(1)")).thenReturn(existingOne);
        stubGeneratedId(42);
        stubVersionResult();

        fileService.addFile(
                addDto("未命名文档", IMAGE_ONLY_CONTENT, true),
                versionDto(null, "未命名文档", IMAGE_ONLY_CONTENT));

        assertEquals("未命名文档(2)", captureCommittedVersion().title());
    }

    @Test
    void addFile_derivedTextTitle_usesNumericSuffixOnCollision() {
        stubAddPrerequisites(emptyRepository());
        File existingBase = existingFile(77);
        existingBase.setTitle("Project plan");
        when(fileMapper.queryByTenantIdRepositoryIdAndTitle(
                1, 1, "Project plan")).thenReturn(existingBase);
        stubGeneratedId(42);
        stubVersionResult();

        fileService.addFile(
                addDto("", "Project plan\nDetails", true),
                versionDto(null, "", "Project plan\nDetails"));

        assertEquals("Project plan(1)", captureCommittedVersion().title());
    }

    @Test
    void addFile_generatedIdMissing_failsBeforeVersionOrPermissionWrites() {
        stubAddPrerequisites(emptyRepository());

        assertThrows(
                IllegalStateException.class,
                () -> fileService.addFile(
                        addDto("design doc", TEXT_ONLY_CONTENT, false),
                        versionDto(null, "design doc", TEXT_ONLY_CONTENT)));

        verify(fileVersionService, never()).addFileVersion(any());
        verify(permissionIntegration, never()).givePermissionByUserID(any());
    }

    @Test
    void addFile_sameDefaultTitleAndCommittedSnapshot_isIdempotent() {
        KnowledgeRepository repository = emptyRepository();
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                anyString(), anyInt(), anyInt(), anyString())).thenReturn(true);
        when(knowledgeRepositoryMapper.queryByIdForUpdate(1)).thenReturn(repository);
        when(fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(List.of(42));

        File existing = existingFile(42);
        existing.setTitle("design doc");
        existing.setContent(TEXT_ONLY_CONTENT);
        when(fileMapper.queryByIdForUpdate(42)).thenReturn(existing);

        FileVersion latest = latestVersion(
                42, 7, "design doc", TEXT_ONLY_CONTENT, DEFAULT_TITLE);
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(latest);

        FileWriteResultVO result = fileService.addFile(
                addDto("design doc", TEXT_ONLY_CONTENT, false),
                versionDto(null, "design doc", TEXT_ONLY_CONTENT));

        assertEquals(42, result.fileId());
        assertEquals(7, result.versionNo());
        verify(fileMapper, never()).addFile(any());
        verify(fileMapper, never()).updateFile(any());
        verify(fileVersionService, never()).addFileVersion(any());
    }

    @Test
    void addFile_ownerPermissionTargetsGeneratedIdAndIsApproved() {
        executeSuccessfulAdd("design doc", TEXT_ONLY_CONTENT, false, 42);

        ArgumentCaptor<PermissionDTO> captor = ArgumentCaptor.forClass(PermissionDTO.class);
        verify(permissionIntegration).givePermissionByUserID(captor.capture());
        PermissionDTO owner = captor.getValue();
        assertEquals(10, owner.getUserId());
        assertEquals("MANAGEABLE", owner.getPermission());
        assertEquals("FILE", owner.getType());
        assertEquals(42, owner.getTargetId());
        verify(permissionIntegration).approvePermission("FILE", 42, 10);
    }

    @Test
    void addFile_batchPermissions_dedupesAndKeepsHighestLevel() {
        KnowledgeRepository repository = repository("10,11,12", "13", "12");
        stubAddPrerequisites(repository);
        stubGeneratedId(42);
        stubVersionResult();

        fileService.addFile(
                addDto("design doc", TEXT_ONLY_CONTENT, false),
                versionDto(null, "design doc", TEXT_ONLY_CONTENT));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PermissionDTO>> captor =
                ArgumentCaptor.forClass((Class<List<PermissionDTO>>) (Class<?>) List.class);
        verify(permissionBatchGivingProducer).sendBatchGiving(captor.capture());

        Map<Integer, String> levelByUserId = new HashMap<>();
        for (PermissionDTO member : captor.getValue()) {
            levelByUserId.put(member.getUserId(), member.getPermission());
            assertEquals("FILE", member.getType());
            assertEquals(42, member.getTargetId());
            assertTrue(member.getExpirationTime() > 0);
        }
        assertEquals(3, levelByUserId.size());
        assertEquals("READABLE", levelByUserId.get(13));
        assertEquals("WRITABLE", levelByUserId.get(11));
        assertEquals("MANAGEABLE", levelByUserId.get(12));
    }

    @Test
    void updateFile_latestEditorState_updatesFileAndAppendsCommittedSnapshot() {
        File existing = stubSuccessfulUpdate(true);

        FileWriteResultVO result = fileService.updateFile(
                updateDto("updated design doc", MULTIPLE_IMAGE_CONTENT, false),
                versionDto(42, "updated design doc", MULTIPLE_IMAGE_CONTENT));

        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).updateFile(fileCaptor.capture());
        assertEquals(existing, fileCaptor.getValue());
        assertEquals("updated design doc", fileCaptor.getValue().getTitle());
        assertEquals(MULTIPLE_IMAGE_CONTENT, fileCaptor.getValue().getContent());

        FileVersionDTO committed = captureCommittedVersion();
        assertEquals(42, committed.fileId());
        assertEquals("updated design doc", committed.title());
        assertEquals(MULTIPLE_IMAGE_CONTENT, committed.content());
        assertEquals(1, committed.repositoryId());
        assertEquals(1, committed.tenantId());
        assertEquals(DEFAULT_TITLE, committed.defaultTitle());
        assertEquals(42, result.fileId());
    }

    @Test
    void updateFile_wrongDefaultTitle_rejectsBeforeFileUpdate() {
        File existing = existingFile(42);
        when(fileMapper.queryById(42)).thenReturn(existing);
        when(fileVersionMapper.queryLatestByFileId(42)).thenReturn(
                latestVersion(42, 3, "design doc", TEXT_ONLY_CONTENT, "stored-key"));

        assertThrows(
                VersionChainConflictException.class,
                () -> fileService.updateFile(
                        updateDtoWithKey(
                                "updated design doc",
                                TEXT_ONLY_CONTENT,
                                false,
                                "different-key"),
                        versionDto(42, "updated design doc", TEXT_ONLY_CONTENT)));

        verify(fileMapper, never()).updateFile(any());
        verify(fileVersionService, never()).addFileVersion(any());
    }

    @Test
    void updateFile_zeroAffectedRows_doesNotAppendVersion() {
        stubSuccessfulUpdate(false);
        when(fileMapper.updateFile(any())).thenReturn(0);

        assertThrows(
                CantFindTargetFileException.class,
                () -> fileService.updateFile(
                        updateDto("updated design doc", TEXT_ONLY_CONTENT, false),
                        versionDto(42, "updated design doc", TEXT_ONLY_CONTENT)));

        verify(fileVersionService, never()).addFileVersion(any());
    }

    @Test
    void addAndUpdatePropagateVersionFailureFromTransactionalBoundary() throws Exception {
        stubAddPrerequisites(emptyRepository());
        stubGeneratedId(42);
        when(fileVersionService.addFileVersion(any()))
                .thenThrow(new IllegalStateException("version insert failed"));

        assertThrows(
                IllegalStateException.class,
                () -> fileService.addFile(
                        addDto("design doc", TEXT_ONLY_CONTENT, false),
                        versionDto(null, "design doc", TEXT_ONLY_CONTENT)));

        verify(permissionIntegration, never()).givePermissionByUserID(any());
        assertTrue(FileServiceImpl.class
                .getMethod("addFile", AddFileDTO.class, FileVersionDTO.class)
                .isAnnotationPresent(Transactional.class));
        assertTrue(FileServiceImpl.class
                .getMethod("updateFile", UpdateFileDTO.class, FileVersionDTO.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void moveToTrashBin_manageableUser_softDeletesFile() {
        File existing = existingFile(42);
        when(fileMapper.queryById(42)).thenReturn(existing);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 10, "MANAGEABLE")).thenReturn(true);
        when(fileMapper.moveToTrashBin(42, 10)).thenReturn(1);

        fileService.moveToTrashBin(new DeleteFileDTO(42, 10));

        verify(fileMapper).moveToTrashBin(42, 10);
        verify(fileMapper, never()).deleteByFileId(anyInt());
        verify(permissionIntegration, never()).deletePermissionsByTarget(
                anyString(), anyInt());
    }

    @Test
    void moveToTrashBin_withoutManageablePermission_doesNotChangeFile() {
        when(fileMapper.queryById(42)).thenReturn(existingFile(42));
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 10, "MANAGEABLE")).thenReturn(false);

        assertThrows(
                UserPermissionDeniedException.class,
                () -> fileService.moveToTrashBin(new DeleteFileDTO(42, 10)));

        verify(fileMapper, never()).moveToTrashBin(anyInt(), anyInt());
    }

    @Test
    void restoreFromTrashBin_manageableUser_makesFileVisibleAgain() {
        File deleted = existingFile(42);
        deleted.setDeleted(true);
        when(fileMapper.queryDeletedById(42)).thenReturn(deleted);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 10, "MANAGEABLE")).thenReturn(true);
        when(fileMapper.restoreFromTrashBin(42, 10)).thenReturn(1);

        fileService.restoreFromTrashBin(new DeleteFileDTO(42, 10));

        verify(fileMapper).restoreFromTrashBin(42, 10);
        verify(fileMapper, never()).deleteByFileId(anyInt());
    }

    @Test
    void restoreFromTrashBin_withoutManageablePermission_keepsFileDeleted() {
        File deleted = existingFile(42);
        deleted.setDeleted(true);
        when(fileMapper.queryDeletedById(42)).thenReturn(deleted);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 10, "MANAGEABLE")).thenReturn(false);

        assertThrows(
                UserPermissionDeniedException.class,
                () -> fileService.restoreFromTrashBin(
                        new DeleteFileDTO(42, 10)));

        verify(fileMapper, never()).restoreFromTrashBin(anyInt(), anyInt());
    }

    @Test
    void deleteByFileId_deletedFile_permanentlyDeletesAndCleansPermissions() {
        File deleted = existingFile(42);
        deleted.setDeleted(true);
        when(fileMapper.queryDeletedById(42)).thenReturn(deleted);
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                "FILE", 42, 10, "MANAGEABLE")).thenReturn(true);
        when(fileMapper.deleteByFileId(42)).thenReturn(1);

        fileService.deleteByFileId(new DeleteFileDTO(42, 10));

        verify(fileMapper).deleteByFileId(42);
        verify(permissionIntegration).deletePermissionsByTarget("FILE", 42);
    }

    @Test
    void queryAllTrashBin_isScopedToTenant() {
        List<File> deletedFiles = List.of(existingFile(42));
        when(fileMapper.queryAllTrashBin(7)).thenReturn(deletedFiles);

        assertEquals(deletedFiles, fileService.queryAllTrashBin(7));
    }

    @Test
    void updateFile_contentChanged_publishesChunkSplitting() {
        stubSuccessfulUpdate(true);

        fileService.updateFile(
                updateDto("design doc", TEXT_ONLY_CONTENT, false),
                versionDto(42, "design doc", TEXT_ONLY_CONTENT));

        ChunkSplittingMessage message = captureSplittingMessage();
        assertEquals(42, message.getFileId());
        assertEquals("design doc", message.getFileName());
        assertEquals(TEXT_ONLY_CONTENT, message.getContent());
        //归属跟着文件走：编辑人是10这里恰好也是owner，但取的必须是file.ownerId
        assertEquals(10, message.getOwnerId());
        assertEquals(1, message.getRepositoryId());
    }

    /**
     * updateFile has no idempotent short circuit, so a submit that changed
     * nothing would otherwise pay for a full re-embedding on every save.
     */
    @Test
    void updateFile_contentUnchangedAndIndexed_skipsChunkSplitting() {
        File existing = stubSuccessfulUpdate(true);
        when(chunkService.queryByRepositoryIdAndFileId(1, 42))
                .thenReturn(List.of(new Chunk()));

        fileService.updateFile(
                updateDto("design doc", existing.getContent(), false),
                versionDto(42, "design doc", existing.getContent()));

        verify(chunkSplittingProducer, never()).sendChunkSplitting(any());
    }

    /**
     * A publish that silently failed, or a message that ended in the DLT,
     * leaves the file with no chunks at all. Re-submitting repairs it.
     */
    @Test
    void updateFile_contentUnchangedButNotIndexed_republishes() {
        File existing = stubSuccessfulUpdate(true);

        fileService.updateFile(
                updateDto("design doc", existing.getContent(), false),
                versionDto(42, "design doc", existing.getContent()));

        assertEquals(42, captureSplittingMessage().getFileId());
    }

    @Test
    void retryCommittedAdd_contentChanged_publishesChunkSplitting() {
        stubRetryPrerequisites(TEXT_ONLY_CONTENT);
        //只有改写那一支会落到主表更新，共用的前置桩里不能放（严格桩会判未使用）
        when(fileMapper.updateFile(any())).thenReturn(1);
        stubVersionResult();

        fileService.addFile(
                addDto("design doc", "a completely rewritten body", false),
                versionDto(null, "design doc", "a completely rewritten body"));

        ChunkSplittingMessage message = captureSplittingMessage();
        assertEquals(42, message.getFileId());
        assertEquals("a completely rewritten body", message.getContent());
    }

    @Test
    void retryCommittedAdd_alreadyCommittedAndIndexed_skipsChunkSplitting() {
        stubRetryPrerequisites(TEXT_ONLY_CONTENT);
        when(chunkService.queryByRepositoryIdAndFileId(1, 42))
                .thenReturn(List.of(new Chunk()));

        fileService.addFile(
                addDto("design doc", TEXT_ONLY_CONTENT, false),
                versionDto(null, "design doc", TEXT_ONLY_CONTENT));

        verify(chunkSplittingProducer, never()).sendChunkSplitting(any());
        verify(fileMapper, never()).updateFile(any());
    }

    @Test
    void retryCommittedAdd_alreadyCommittedButNotIndexed_republishes() {
        stubRetryPrerequisites(TEXT_ONLY_CONTENT);

        fileService.addFile(
                addDto("design doc", TEXT_ONLY_CONTENT, false),
                versionDto(null, "design doc", TEXT_ONLY_CONTENT));

        assertEquals(TEXT_ONLY_CONTENT, captureSplittingMessage().getContent());
        //补发不等于重写：这条分支仍然什么都不写
        verify(fileMapper, never()).updateFile(any());
    }

    private void stubRetryPrerequisites(String committedContent) {
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                anyString(), anyInt(), anyInt(), anyString())).thenReturn(true);
        when(knowledgeRepositoryMapper.queryByIdForUpdate(1))
                .thenReturn(emptyRepository());
        when(fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(List.of(42));

        File existing = existingFile(42);
        existing.setContent(committedContent);
        when(fileMapper.queryByIdForUpdate(42)).thenReturn(existing);
        when(fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(
                latestVersion(42, 7, "design doc", committedContent, DEFAULT_TITLE));
    }

    private ChunkSplittingMessage captureSplittingMessage() {
        ArgumentCaptor<ChunkSplittingMessage> captor =
                ArgumentCaptor.forClass(ChunkSplittingMessage.class);
        verify(chunkSplittingProducer).sendChunkSplitting(captor.capture());
        return captor.getValue();
    }

    private FileWriteResultVO executeSuccessfulAdd(
            String title,
            String content,
            boolean autoTitle,
            int generatedId) {
        stubAddPrerequisites(emptyRepository());
        stubGeneratedId(generatedId);
        stubVersionResult();
        return fileService.addFile(
                addDto(title, content, autoTitle),
                versionDto(null, title, content));
    }

    private void stubAddPrerequisites(KnowledgeRepository repository) {
        when(permissionIntegration.checkPermissionByTypeTargetUserId(
                anyString(), anyInt(), anyInt(), anyString())).thenReturn(true);
        when(knowledgeRepositoryMapper.queryByIdForUpdate(1)).thenReturn(repository);
    }

    private void stubGeneratedId(int id) {
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId(id);
            return null;
        }).when(fileMapper).addFile(any(File.class));
    }

    private void stubVersionResult() {
        when(fileVersionService.addFileVersion(any())).thenAnswer(invocation -> {
            FileVersionDTO version = invocation.getArgument(0);
            return new FileWriteResultVO(
                    version.fileId(),
                    4,
                    version.defaultTitle(),
                    version.title());
        });
    }

    private File stubSuccessfulUpdate(boolean versionWriteExpected) {
        File existing = existingFile(42);
        when(fileMapper.queryById(42)).thenReturn(existing);
        when(fileVersionMapper.queryLatestByFileId(42)).thenReturn(
                latestVersion(42, 3, "design doc", SINGLE_IMAGE_CONTENT, DEFAULT_TITLE));
        when(fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                1, 1, DEFAULT_TITLE)).thenReturn(List.of(42));
        when(fileMapper.queryByIdForUpdate(42)).thenReturn(existing);
        when(knowledgeRepositoryMapper.queryByIdForUpdate(1))
                .thenReturn(emptyRepository());
        when(fileMapper.updateFile(any())).thenReturn(1);
        if (versionWriteExpected) {
            stubVersionResult();
        }
        return existing;
    }

    private FileVersionDTO captureCommittedVersion() {
        ArgumentCaptor<FileVersionDTO> captor =
                ArgumentCaptor.forClass(FileVersionDTO.class);
        verify(fileVersionService).addFileVersion(captor.capture());
        return captor.getValue();
    }

    private static AddFileDTO addDto(
            String title,
            String content,
            boolean autoTitle) {
        return new AddFileDTO(
                1,
                10,
                title,
                content,
                null,
                "10,11",
                "10",
                "",
                new Date(),
                10,
                1,
                false,
                DEFAULT_TITLE,
                autoTitle);
    }

    private static UpdateFileDTO updateDto(
            String title,
            String content,
            boolean autoTitle) {
        return updateDtoWithKey(title, content, autoTitle, DEFAULT_TITLE);
    }

    private static UpdateFileDTO updateDtoWithKey(
            String title,
            String content,
            boolean autoTitle,
            String defaultTitle) {
        return new UpdateFileDTO(
                42,
                title,
                content,
                "10,11",
                "10",
                "10",
                10,
                false,
                defaultTitle,
                autoTitle);
    }

    private static FileVersionDTO versionDto(
            Integer fileId,
            String title,
            String content) {
        return new FileVersionDTO(
                null,
                fileId,
                0,
                title,
                content,
                10,
                null,
                null,
                1,
                1,
                DEFAULT_TITLE);
    }

    private static File existingFile(int id) {
        File file = new File();
        file.setId(id);
        file.setOwnerId(10);
        file.setRepositoryId(1);
        file.setTenantId(1);
        file.setTitle("design doc");
        file.setContent(SINGLE_IMAGE_CONTENT);
        file.setWriterList("10");
        file.setReaderList("10");
        file.setManageableList("10");
        return file;
    }

    private static FileVersion latestVersion(
            int fileId,
            int versionNo,
            String title,
            String content,
            String defaultTitle) {
        FileVersion version = new FileVersion();
        version.setFileId(fileId);
        version.setVersionNo(versionNo);
        version.setTitle(title);
        version.setContent(content);
        version.setDefaultTitle(defaultTitle);
        version.setRepositoryId(1);
        version.setTenantId(1);
        return version;
    }

    private static KnowledgeRepository emptyRepository() {
        return repository("", "", "");
    }

    private static KnowledgeRepository repository(
            String writableList,
            String readableList,
            String manageableList) {
        KnowledgeRepository repository = new KnowledgeRepository();
        repository.setId(1L);
        repository.setTenantId(1);
        repository.setWritableList(writableList);
        repository.setReadableList(readableList);
        repository.setManageableList(manageableList);
        return repository;
    }
}
