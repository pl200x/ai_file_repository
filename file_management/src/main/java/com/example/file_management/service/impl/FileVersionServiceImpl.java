package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;
import com.example.file_management.entity.FileVersion;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.enums.PermissionTargetType;
import com.example.file_management.enums.PermissionType;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.InvalidFileVersionException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.mapper.FileVersionMapper;
import com.example.file_management.mapper.KnowledgeRepositoryMapper;
import com.example.file_management.service.FileVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FileVersionServiceImpl implements FileVersionService {
    private static final int MAX_DEFAULT_TITLE_LENGTH = 255;

    @Autowired
    private FileVersionMapper fileVersionMapper;
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private KnowledgeRepositoryMapper knowledgeRepositoryMapper;
    @Autowired
    private PermissionIntegration permissionIntegration;

    @Override
    public List<FileVersion> queryVersionsByFileId(int fileId, int userId) {
        checkFileReadable(fileId, userId);
        return fileVersionMapper.queryByFileId(fileId);
    }

    @Override
    public List<FileVersion> queryVersionsByDefaultTitle(
            String defaultTitle,
            int repositoryId,
            int tenantId,
            int userId) {
        validateChainIdentity(defaultTitle, repositoryId, tenantId);
        List<FileVersion> versions =
                fileVersionMapper.queryByDefaultTitle(tenantId, repositoryId, defaultTitle.trim());
        Integer linkedFileId = resolveLinkedFileId(
                new FileVersionDTO(
                        null, null, 0, "", "", userId,
                        null, null, repositoryId, tenantId, defaultTitle),
                versions == null || versions.isEmpty() ? null : versions.get(0));
        if (linkedFileId != null) {
            checkFileReadable(linkedFileId, userId);
        } else {
            checkDraftReadable(versions, repositoryId, tenantId, userId);
        }
        return versions;
    }

    @Override
    public FileVersion queryVersionDetail(int fileId, int versionNo, int userId) {
        checkFileReadable(fileId, userId);
        FileVersion version = fileVersionMapper.queryByFileIdAndVersionNo(fileId, versionNo);
        if (version == null) {
            throw new CantFindTargetFileException("The target version does not exist");
        }
        return version;
    }

    /**
     * The initialize request is intentionally idempotent. Retrying the request
     * with the same tenant/repository/defaultTitle returns the current chain
     * head instead of creating another empty snapshot.
     */
    @Override
    @Transactional
    public FileWriteResultVO initializeFileVersion(FileVersionDTO dto) {
        String defaultTitle = normalizedDefaultTitle(dto);

        FileVersion latest = fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                dto.tenantId(), dto.repositoryId(), defaultTitle);
        if (latest != null) {
            Integer linkedFileId = resolveLinkedFileId(dto, latest);
            checkCanWrite(dto, linkedFileId);
            return result(latest, linkedFileId);
        }

        checkRepositoryWritable(dto);
        lockAndValidateRepository(dto.repositoryId(), dto.tenantId());

        // A repository-row lock protects the no-row initialization case. Once
        // v1 exists, subsequent writes lock the latest version row instead.
        latest = fileVersionMapper.queryLatestByDefaultTitleForUpdate(
                dto.tenantId(), dto.repositoryId(), defaultTitle);
        if (latest != null) {
            Integer linkedFileId = resolveLinkedFileId(dto, latest);
            checkCanWrite(dto, linkedFileId);
            return result(latest, linkedFileId);
        }

        FileVersion initial = buildVersion(dto, null, 1, defaultTitle);
        fileVersionMapper.insertVersion(initial);
        return result(initial, null);
    }

    /**
     * Appends a snapshot to the chain identified exclusively by defaultTitle.
     * A mutable display title is never used to locate either the version chain
     * or its materialized File.
     */
    @Override
    @Transactional
    public FileWriteResultVO addFileVersion(FileVersionDTO dto) {
        String defaultTitle = normalizedDefaultTitle(dto);

        FileVersion observedLatest = fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                dto.tenantId(), dto.repositoryId(), defaultTitle);
        if (observedLatest == null) {
            // SELECT ... FOR UPDATE cannot lock an absent row. Serializing the
            // first insert on the repository row prevents duplicate v1 rows.
            lockAndValidateRepository(dto.repositoryId(), dto.tenantId());
        }

        FileVersion latest = fileVersionMapper.queryLatestByDefaultTitleForUpdate(
                dto.tenantId(), dto.repositoryId(), defaultTitle);
        // This fallback only covers a row disappearing between the optimistic
        // read and lock query; versions are normally append-only.
        if (latest == null) {
            latest = observedLatest;
        }

        Integer linkedFileId = resolveLinkedFileId(dto, latest);
        Integer requestedFileId = dto.fileId();
        if (requestedFileId != null && linkedFileId != null
                && !requestedFileId.equals(linkedFileId)) {
            throw new VersionChainConflictException(
                    "defaultTitle is already linked to a different file");
        }

        Integer targetFileId = linkedFileId != null ? linkedFileId : requestedFileId;
        checkCanWrite(dto, targetFileId);

        int latestVersionNo = latest == null ? 0 : latest.getVersionNo();
        if (dto.versionNo() > 0 && dto.versionNo() != latestVersionNo) {
            throw new VersionChainConflictException(
                    "The version chain changed; expected v" + dto.versionNo()
                            + " but latest is v" + latestVersionNo);
        }

        if (targetFileId != null && linkedFileId == null) {
            fileVersionMapper.adoptDraftVersions(
                    dto.tenantId(), dto.repositoryId(), defaultTitle, targetFileId);
        }

        FileVersion next = buildVersion(
                dto, targetFileId, latestVersionNo + 1, defaultTitle);
        fileVersionMapper.insertVersion(next);
        return result(next, targetFileId);
    }

    private FileVersion buildVersion(
            FileVersionDTO dto,
            Integer fileId,
            int versionNo,
            String defaultTitle) {
        FileVersion version = new FileVersion();
        version.setFileId(fileId);
        version.setVersionNo(versionNo);
        version.setTitle(dto.title() == null ? "" : dto.title());
        version.setContent(dto.content() == null ? "" : dto.content());
        version.setEditorId(dto.editorId());
        version.setRepositoryId(dto.repositoryId());
        version.setTenantId(dto.tenantId());
        version.setDefaultTitle(defaultTitle);
        return version;
    }

    private FileWriteResultVO result(FileVersion version, Integer fileId) {
        return new FileWriteResultVO(
                fileId != null ? fileId : version.getFileId(),
                version.getVersionNo(),
                version.getDefaultTitle(),
                version.getTitle() == null ? "" : version.getTitle());
    }

    private String normalizedDefaultTitle(FileVersionDTO dto) {
        validateChainIdentity(dto.defaultTitle(), dto.repositoryId(), dto.tenantId());
        if (dto.editorId() <= 0) {
            throw new InvalidFileVersionException("editorId must be positive");
        }
        return dto.defaultTitle().trim();
    }

    private void validateChainIdentity(String defaultTitle, int repositoryId, int tenantId) {
        if (defaultTitle == null || defaultTitle.isBlank()) {
            throw new InvalidFileVersionException("defaultTitle is required");
        }
        if (defaultTitle.trim().length() > MAX_DEFAULT_TITLE_LENGTH) {
            throw new InvalidFileVersionException("defaultTitle is too long");
        }
        if (repositoryId <= 0 || tenantId <= 0) {
            throw new InvalidFileVersionException("tenantId and repositoryId must be positive");
        }
    }

    private Integer resolveLinkedFileId(FileVersionDTO dto, FileVersion latest) {
        List<Integer> fileIds = fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                dto.tenantId(), dto.repositoryId(), dto.defaultTitle().trim());
        if (fileIds != null && fileIds.size() > 1) {
            throw new VersionChainConflictException(
                    "defaultTitle is linked to more than one file");
        }
        if (fileIds != null && fileIds.size() == 1) {
            return fileIds.get(0);
        }
        return latest == null ? null : latest.getFileId();
    }

    private void checkCanWrite(FileVersionDTO dto, Integer fileId) {
        if (fileId == null) {
            checkRepositoryWritable(dto);
            return;
        }

        File file = fileMapper.queryById(fileId);
        if (file == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }
        if (file.getTenantId() != dto.tenantId()
                || file.getRepositoryId() != dto.repositoryId()) {
            throw new VersionChainConflictException(
                    "defaultTitle and file belong to different repositories");
        }

        // addFile appends its committed snapshot before the new FILE permission
        // is created. The owner is intrinsically allowed to write that snapshot.
        if (file.getOwnerId() == dto.editorId()) {
            return;
        }
        boolean canWrite = permissionIntegration.checkPermissionByTypeTargetUserId(
                PermissionTargetType.FILE.getCode(),
                fileId,
                dto.editorId(),
                PermissionType.WRITABLE.getCode());
        if (!canWrite) {
            throw new UserPermissionDeniedException(
                    "you don't have permission to write in this file");
        }
    }

    private void checkRepositoryWritable(FileVersionDTO dto) {
        KnowledgeRepository repository = knowledgeRepositoryMapper.queryById(dto.repositoryId());
        if (repository == null || repository.getTenantId() != dto.tenantId()) {
            throw new CantFindTargetFileException("The target repository does not exist");
        }
        boolean canWrite = permissionIntegration.checkPermissionByTypeTargetUserId(
                PermissionTargetType.KNOWLEDGE_REPOSITORY.getCode(),
                dto.repositoryId(),
                dto.editorId(),
                PermissionType.WRITABLE.getCode());
        if (!canWrite) {
            throw new UserPermissionDeniedException(
                    "you don't have access to save a draft in this repository");
        }
    }

    private void checkDraftReadable(
            List<FileVersion> versions,
            int repositoryId,
            int tenantId,
            int userId) {
        KnowledgeRepository repository = knowledgeRepositoryMapper.queryById(repositoryId);
        if (repository == null || repository.getTenantId() != tenantId) {
            throw new CantFindTargetFileException("The target repository does not exist");
        }
        if (repository.getOwnerId() == userId) {
            return;
        }
        if (versions != null && !versions.isEmpty()) {
            FileVersion firstVersion = versions.get(versions.size() - 1);
            if (firstVersion.getEditorId() == userId) {
                return;
            }
        }
        boolean canRead = permissionIntegration.checkPermissionByTypeTargetUserId(
                PermissionTargetType.KNOWLEDGE_REPOSITORY.getCode(),
                repositoryId,
                userId,
                PermissionType.READABLE.getCode());
        if (!canRead) {
            throw new UserPermissionDeniedException(
                    "you don't have permission to read this draft");
        }
    }

    private KnowledgeRepository lockAndValidateRepository(int repositoryId, int tenantId) {
        KnowledgeRepository repository = knowledgeRepositoryMapper.queryByIdForUpdate(repositoryId);
        if (repository == null || repository.getTenantId() != tenantId) {
            throw new CantFindTargetFileException("The target repository does not exist");
        }
        return repository;
    }

    // Viewing version history is equivalent to reading the file.
    private void checkFileReadable(int fileId, int userId) {
        File file = fileMapper.queryById(fileId);
        if (file == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }
        if (file.getOwnerId() == userId) {
            return;
        }
        boolean canRead = permissionIntegration.checkPermissionByTypeTargetUserId(
                PermissionTargetType.FILE.getCode(),
                fileId,
                userId,
                PermissionType.READABLE.getCode());
        if (!canRead) {
            throw new UserPermissionDeniedException("You don't have right to read this file");
        }
    }
}
