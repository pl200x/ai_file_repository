package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.controller.dto.DeleteFileDTO;
import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.dto.UpdateFileDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;
import com.example.file_management.entity.FileVersion;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.entity.User;
import com.example.file_management.enums.PermissionTargetType;
import com.example.file_management.enums.PermissionType;
import com.example.file_management.exception.CantFindTargetFileException;
import com.example.file_management.exception.FileTitleConflictException;
import com.example.file_management.exception.UserNotExistException;
import com.example.file_management.exception.UserPermissionDeniedException;
import com.example.file_management.exception.VersionChainConflictException;
import com.example.file_management.integration.PermissionDTO;
import com.example.file_management.integration.PermissionIntegration;
import com.example.file_management.mapper.FileMapper;
import com.example.file_management.mapper.FileVersionMapper;
import com.example.file_management.mapper.KnowledgeRepositoryMapper;
import com.example.file_management.mapper.UserMapper;
import com.example.file_management.service.FileService;
import com.example.file_management.service.FileVersionService;
import com.example.file_management.service.producer.PermissionBatchGivingProducer;
import com.example.file_management.util.InfinityDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class FileServiceImpl implements FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final int AUTO_TITLE_MAX_LENGTH = 50;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_DEFAULT_TITLE_LENGTH = 255;
    private static final Pattern IMAGE_MARKER_PATTERN = Pattern.compile(
            "^\\[\\[IMAGE:data:image/(?:png|jpe?g|jpg|gif|webp);base64,"
                    + "[A-Za-z0-9+/]+={0,2}\\]\\]$");

    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private FileVersionMapper fileVersionMapper;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PermissionIntegration permissionIntegration;
    @Autowired
    private KnowledgeRepositoryMapper knowledgeRepositoryMapper;
    @Autowired
    private PermissionBatchGivingProducer permissionBatchGivingProducer;
    @Autowired
    private FileVersionService fileVersionService;
    @Override
    @Transactional
    public FileWriteResultVO addFile(AddFileDTO fileDTO, FileVersionDTO fileVersionDTO) {
        //新建文档，先检查当前repository权限
        //用户有repository权限才可以新建文档
        //String type, int targetId, int userId

        boolean userPermission = permissionIntegration.checkPermissionByTypeTargetUserId(PermissionTargetType.KNOWLEDGE_REPOSITORY.getCode(),
                fileDTO.repositoryId(), fileDTO.ownerId(),"WRITABLE");
        if(!userPermission){
            throw new UserPermissionDeniedException("you don't have access to create a new file in this repository");
        }

        String defaultTitle = requireDefaultTitle(fileDTO.defaultTitle());
        KnowledgeRepository repository = knowledgeRepositoryMapper.queryByIdForUpdate(fileDTO.repositoryId());
        if (repository == null || repository.getTenantId() != fileDTO.tenantId()) {
            throw new CantFindTargetFileException("The target repository does not exist");
        }

        Integer alreadyLinkedFileId = resolveLinkedFileId(
                fileDTO.tenantId(), fileDTO.repositoryId(), defaultTitle);
        if (alreadyLinkedFileId != null) {
            return retryCommittedAdd(
                    fileDTO, fileVersionDTO, defaultTitle, alreadyLinkedFileId);
        }

        String effectiveTitle = resolveEffectiveTitle(
                fileDTO.title(), fileDTO.content(), fileDTO.autoTitle(),
                fileDTO.tenantId(), fileDTO.repositoryId(), null);
        File addFile = buildFile(fileDTO);
        addFile.setTitle(effectiveTitle);

        fileMapper.addFile(addFile);
        int addedFileId = addFile.getId();
        if (addedFileId <= 0) {
            throw new IllegalStateException("Database did not return the new file id");
        }
        //first add manageable permission to owner
        //permission服务的applyPermissionLevel会级联：manageable自动带上writable和readable，一条就够
        PermissionDTO permissionDTO = new PermissionDTO();

        permissionDTO.setUserId(fileDTO.ownerId());
        permissionDTO.setPermission(PermissionType.MANAGEABLE.getCode());
        permissionDTO.setType(PermissionTargetType.FILE.getCode());
        permissionDTO.setTargetId(addedFileId);

        FileVersionDTO committedVersion = new FileVersionDTO(
                fileVersionDTO.id(), addedFileId, fileVersionDTO.versionNo(),
                effectiveTitle, fileDTO.content(), fileDTO.ownerId(),
                fileVersionDTO.openTime(), fileVersionDTO.lastMergeTime(),
                fileDTO.repositoryId(), fileDTO.tenantId(), defaultTitle);
        FileWriteResultVO result = fileVersionService.addFileVersion(committedVersion);

        permissionDTO.setExpirationTime(InfinityDateTime.getInfinityDate().getTime() - System.currentTimeMillis());
        permissionIntegration.givePermissionByUserID(permissionDTO);
        permissionIntegration.approvePermission(
                PermissionTargetType.FILE.getCode(), addedFileId, fileDTO.ownerId());

        //给知识库三个权限名单里的人加对应级别的文档权限：
        //permission表以(type, target_id, user_id)为唯一键，一个用户只能有一条记录，
        //所以三个名单先做并集去重，每人取最高级别（低级别先写入，高级别覆盖），一次batch发出；
        //并集里所有人至少拿到readable由permission服务的级联保证
        Map<Integer, String> levelByUserId = new LinkedHashMap<>();
        for (Integer memberId : parseIdList(repository.getReadableList())) {
            levelByUserId.put(memberId, PermissionType.READABLE.getCode());
        }
        for (Integer memberId : parseIdList(repository.getWritableList())) {
            levelByUserId.put(memberId, PermissionType.WRITABLE.getCode());
        }
        for (Integer memberId : parseIdList(repository.getManageableList())) {
            levelByUserId.put(memberId, PermissionType.MANAGEABLE.getCode());
        }
        //owner上面已单独授权，batch里再出现会撞唯一键导致整批失败
        levelByUserId.remove(fileDTO.ownerId());

        if (levelByUserId.isEmpty()) {
            return result;
        }

        List<PermissionDTO> permissionDTOList = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : levelByUserId.entrySet()) {
            PermissionDTO currentDTO = new PermissionDTO();
            currentDTO.setUserId(entry.getKey());
            currentDTO.setPermission(entry.getValue());
            currentDTO.setType(PermissionTargetType.FILE.getCode());
            currentDTO.setTargetId(addedFileId);
            currentDTO.setExpirationTime(InfinityDateTime.getInfinityDate().getTime() - System.currentTimeMillis());
            permissionDTOList.add(currentDTO);
        }
        //owner之外的成员授权走kafka异步：addFile不再等permission服务的batch落库
        permissionBatchGivingProducer.sendBatchGiving(addedFileId, permissionDTOList);
        return result;
    }

    private List<Integer> parseIdList(String idList) {
        List<Integer> ids = new ArrayList<>();
        if (idList == null || idList.isBlank()) {
            return ids;
        }
        for (String each : idList.split(",")) {
            String trimmed = each.trim();
            if (!trimmed.isEmpty()) {
                ids.add(Integer.parseInt(trimmed));
            }
        }
        return ids;
    }

    private FileWriteResultVO retryCommittedAdd(
            AddFileDTO fileDTO,
            FileVersionDTO fileVersionDTO,
            String defaultTitle,
            int fileId) {
        File existing = fileMapper.queryByIdForUpdate(fileId);
        if (existing == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }
        if (existing.getTenantId() != fileDTO.tenantId()
                || existing.getRepositoryId() != fileDTO.repositoryId()) {
            throw new VersionChainConflictException(
                    "defaultTitle and file belong to different repositories");
        }

        boolean canWrite = existing.getOwnerId() == fileDTO.ownerId()
                || permissionIntegration.checkPermissionByTypeTargetUserId(
                PermissionTargetType.FILE.getCode(),
                existing.getId(),
                fileDTO.ownerId(),
                PermissionType.WRITABLE.getCode());
        if (!canWrite) {
            throw new UserPermissionDeniedException(
                    "you don't have permission to write in this file");
        }

        String effectiveTitle = resolveEffectiveTitle(
                fileDTO.title(), fileDTO.content(), fileDTO.autoTitle(),
                fileDTO.tenantId(), fileDTO.repositoryId(), existing.getId());
        FileVersion latest = fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                fileDTO.tenantId(), fileDTO.repositoryId(), defaultTitle);

        // A lost HTTP response may make the client retry addFile. Returning the
        // already committed head prevents duplicate Files and duplicate versions.
        if (latest != null
                && same(existing.getTitle(), effectiveTitle)
                && same(existing.getContent(), fileDTO.content())
                && same(latest.getTitle(), effectiveTitle)
                && same(latest.getContent(), fileDTO.content())) {
            return new FileWriteResultVO(
                    existing.getId(), latest.getVersionNo(), defaultTitle, effectiveTitle);
        }

        existing.setTitle(effectiveTitle);
        existing.setContent(fileDTO.content() == null ? "" : fileDTO.content());
        existing.setLatestModifiedUserId(fileDTO.ownerId());
        if (fileMapper.updateFile(existing) != 1) {
            throw new CantFindTargetFileException(
                    "The target file changed or no longer exists");
        }

        FileVersionDTO retryVersion = new FileVersionDTO(
                fileVersionDTO.id(), existing.getId(), fileVersionDTO.versionNo(),
                effectiveTitle, fileDTO.content(), fileDTO.ownerId(),
                fileVersionDTO.openTime(), fileVersionDTO.lastMergeTime(),
                existing.getRepositoryId(), existing.getTenantId(), defaultTitle);
        return fileVersionService.addFileVersion(retryVersion);
    }

    private String requireDefaultTitle(String defaultTitle) {
        if (defaultTitle == null || defaultTitle.isBlank()) {
            throw new IllegalArgumentException("defaultTitle is required");
        }
        String normalized = defaultTitle.trim();
        if (normalized.length() > MAX_DEFAULT_TITLE_LENGTH) {
            throw new IllegalArgumentException("defaultTitle is too long");
        }
        return normalized;
    }

    private String resolveExistingDefaultTitle(String requestedDefaultTitle, File file) {
        FileVersion latestByFileId = fileVersionMapper.queryLatestByFileId(file.getId());
        String storedDefaultTitle = latestByFileId == null
                ? null
                : latestByFileId.getDefaultTitle();

        if (requestedDefaultTitle != null && !requestedDefaultTitle.isBlank()) {
            String requested = requestedDefaultTitle.trim();
            if (storedDefaultTitle != null
                    && !storedDefaultTitle.isBlank()
                    && !storedDefaultTitle.equals(requested)) {
                throw new VersionChainConflictException(
                        "defaultTitle does not belong to the requested file");
            }
            return requested;
        }
        if (storedDefaultTitle != null && !storedDefaultTitle.isBlank()) {
            return storedDefaultTitle;
        }

        // Backward-compatible bridge for a File created before version chains
        // were mandatory. The first update persists this stable key in v1.
        return "legacy-file-" + file.getTenantId() + "-"
                + file.getRepositoryId() + "-" + file.getId();
    }

    private Integer resolveLinkedFileId(int tenantId, int repositoryId, String defaultTitle) {
        List<Integer> linkedFileIds = fileVersionMapper.queryLinkedFileIdsByDefaultTitle(
                tenantId, repositoryId, defaultTitle);
        if (linkedFileIds != null && linkedFileIds.size() > 1) {
            throw new VersionChainConflictException(
                    "defaultTitle is linked to more than one file");
        }
        if (linkedFileIds != null && linkedFileIds.size() == 1) {
            return linkedFileIds.get(0);
        }
        FileVersion latest = fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                tenantId, repositoryId, defaultTitle);
        return latest == null ? null : latest.getFileId();
    }

    private String resolveEffectiveTitle(
            String requestedTitle,
            String content,
            boolean autoTitle,
            int tenantId,
            int repositoryId,
            Integer excludedFileId) {
        boolean shouldGenerate = autoTitle
                || requestedTitle == null
                || requestedTitle.isBlank();
        if (!shouldGenerate) {
            String explicitTitle = requestedTitle.trim();
            if (explicitTitle.codePointCount(0, explicitTitle.length()) > MAX_TITLE_LENGTH) {
                throw new IllegalArgumentException("title is too long");
            }
            File conflict = fileMapper.queryByTenantIdRepositoryIdAndTitle(
                    tenantId, repositoryId, explicitTitle);
            if (conflict != null
                    && (excludedFileId == null || conflict.getId() != excludedFileId)) {
                throw new FileTitleConflictException(
                        "A file named \"" + explicitTitle
                                + "\" already exists in this repository");
            }
            return explicitTitle;
        }

        String textTitle = firstTextLine(content);
        if (textTitle == null) {
            // There is intentionally no bare "未命名文档": the first pure-image
            // or empty document starts at 未命名文档(1).
            return firstAvailableGeneratedTitle(
                    "未命名文档", 1, tenantId, repositoryId, excludedFileId);
        }

        String base = truncateCodePoints(textTitle, AUTO_TITLE_MAX_LENGTH);
        if (!isTitleTaken(tenantId, repositoryId, base, excludedFileId)) {
            return base;
        }
        return firstAvailableGeneratedTitle(
                base, 1, tenantId, repositoryId, excludedFileId);
    }

    private String firstTextLine(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        for (String line : normalized.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || IMAGE_MARKER_PATTERN.matcher(trimmed).matches()) {
                continue;
            }
            return trimmed.replaceAll("\\s+", " ");
        }
        return null;
    }

    private String firstAvailableGeneratedTitle(
            String base,
            int firstSuffix,
            int tenantId,
            int repositoryId,
            Integer excludedFileId) {
        for (int suffix = firstSuffix; suffix < Integer.MAX_VALUE; suffix++) {
            String suffixText = "(" + suffix + ")";
            String shortenedBase = truncateCodePoints(
                    base, Math.max(1, AUTO_TITLE_MAX_LENGTH - suffixText.length()));
            String candidate = shortenedBase + suffixText;
            if (!isTitleTaken(tenantId, repositoryId, candidate, excludedFileId)) {
                return candidate;
            }
        }
        throw new FileTitleConflictException(
                "Unable to allocate a unique title in this repository");
    }

    private boolean isTitleTaken(
            int tenantId,
            int repositoryId,
            String title,
            Integer excludedFileId) {
        File existing = fileMapper.queryByTenantIdRepositoryIdAndTitle(
                tenantId, repositoryId, title);
        return existing != null
                && (excludedFileId == null || existing.getId() != excludedFileId);
    }

    private String truncateCodePoints(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    private boolean same(String left, String right) {
        String normalizedLeft = left == null ? "" : left;
        String normalizedRight = right == null ? "" : right;
        return normalizedLeft.equals(normalizedRight);
    }

    @Override
    @Transactional
    public FileWriteResultVO updateFile(UpdateFileDTO updateFileDTO, FileVersionDTO fileVersionDTO) {
        File requestedFile = fileMapper.queryById(updateFileDTO.id());
        if (requestedFile == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }

        String defaultTitle = resolveExistingDefaultTitle(
                updateFileDTO.defaultTitle(), requestedFile);
        FileVersion chainHead =
                fileVersionMapper.queryLastByTenantIdRepositoryIdAndTitle(
                        requestedFile.getTenantId(),
                        requestedFile.getRepositoryId(),
                        defaultTitle);
        Integer linkedFileId = resolveLinkedFileId(
                requestedFile.getTenantId(), requestedFile.getRepositoryId(), defaultTitle);
        if (linkedFileId != null && linkedFileId != updateFileDTO.id()) {
            throw new VersionChainConflictException(
                    "defaultTitle is linked to a different file");
        }
        if (chainHead != null && linkedFileId == null) {
            throw new VersionChainConflictException(
                    "defaultTitle still identifies an uncommitted draft; create the file first");
        }

        // New clients always arrive with an already-linked chain. The null case
        // is retained only when a legacy File has no version chain at all.
        int resolvedFileId = linkedFileId == null ? updateFileDTO.id() : linkedFileId;
        File file = fileMapper.queryByIdForUpdate(resolvedFileId);
        if (file == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }
        if (file.getTenantId() != requestedFile.getTenantId()
                || file.getRepositoryId() != requestedFile.getRepositoryId()) {
            throw new VersionChainConflictException(
                    "defaultTitle and file belong to different repositories");
        }

        boolean canCurrentUserWrite = file.getOwnerId() == updateFileDTO.latestModifiedUserId()
                || permissionIntegration.checkPermissionByTypeTargetUserId(
                PermissionTargetType.FILE.getCode(),
                resolvedFileId,
                updateFileDTO.latestModifiedUserId(),
                PermissionType.WRITABLE.getCode());
        if (!canCurrentUserWrite) {
            throw new UserPermissionDeniedException("you don't have permission to write in this file");
        }

        KnowledgeRepository repository =
                knowledgeRepositoryMapper.queryByIdForUpdate(file.getRepositoryId());
        if (repository == null || repository.getTenantId() != file.getTenantId()) {
            throw new CantFindTargetFileException("The target repository does not exist");
        }
        String effectiveTitle = resolveEffectiveTitle(
                updateFileDTO.title(), updateFileDTO.content(), updateFileDTO.autoTitle(),
                file.getTenantId(), file.getRepositoryId(), file.getId());

        int affectedRows = fileMapper.updateFile(
                buildFile(updateFileDTO, file, effectiveTitle));
        if (affectedRows != 1) {
            throw new CantFindTargetFileException(
                    "The target file changed or no longer exists");
        }

        //提交即落一条完整快照版本，和主表更新同一事务。
        //UpdateFileDTO里没有tenant/repo，DTO这两个字段以file行为准重建，防止客户端传错串链；
        //defaultTitle传null，文档已存在时service会沿用版本链上已有的值
        FileVersionDTO versionDTO = new FileVersionDTO(fileVersionDTO.id(), file.getId(), fileVersionDTO.versionNo(),
                effectiveTitle, updateFileDTO.content(), fileVersionDTO.editorId(),
                fileVersionDTO.openTime(), fileVersionDTO.lastMergeTime(), file.getRepositoryId(), file.getTenantId(),
                defaultTitle);
        return fileVersionService.addFileVersion(versionDTO);
    }

    @Override
    @Transactional
    public void deleteByFileId(DeleteFileDTO deleteFileDTO) {
        File file = fileMapper.queryById(deleteFileDTO.id());
        if (file == null) {
            file = fileMapper.queryDeletedById(deleteFileDTO.id());
        }
        if (file == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }

        //权限真源在permission服务：file.manageableList只是创建时的初始名单，
        //通过inviteAccess后来拿到manageable的人不在名单里，所以这里必须查permission服务
        boolean canCurrentUserManage = permissionIntegration.checkPermissionByTypeTargetUserId(PermissionTargetType.FILE.getCode(),
                deleteFileDTO.id(), deleteFileDTO.latestModifiedUserId(), PermissionType.MANAGEABLE.getCode());
        if(!canCurrentUserManage){
           throw new UserPermissionDeniedException("You don't have right to continue this delete operation");
        }

        //先删文件行再删权限：若反过来，权限删完文件删失败会导致没人再有权限删这份文件；
        //而权限清理失败只会残留指向已不存在文件的记录，无安全影响，记日志即可
        //TODO:权限清理后续引入kafka改为异步
        if (fileMapper.deleteByFileId(deleteFileDTO.id()) != 1) {
            throw new CantFindTargetFileException(
                    "The target file changed or no longer exists");
        }

        try {
            permissionIntegration.deletePermissionsByTarget(PermissionTargetType.FILE.getCode(), deleteFileDTO.id());
        } catch (Exception e) {
            logger.warn("file {} deleted but cleaning up its permissions failed, needs retry", deleteFileDTO.id(), e);
        }
    }

    @Override
    @Transactional
    public void moveToTrashBin(DeleteFileDTO deleteFileDTO) {
        File file = fileMapper.queryById(deleteFileDTO.id());
        if (file == null) {
            throw new CantFindTargetFileException("The target file does not exist");
        }

        boolean canCurrentUserManage =
                permissionIntegration.checkPermissionByTypeTargetUserId(
                        PermissionTargetType.FILE.getCode(),
                        deleteFileDTO.id(),
                        deleteFileDTO.latestModifiedUserId(),
                        PermissionType.MANAGEABLE.getCode());
        if (!canCurrentUserManage) {
            throw new UserPermissionDeniedException(
                    "You don't have right to move this file to trash");
        }

        if (fileMapper.moveToTrashBin(
                deleteFileDTO.id(), deleteFileDTO.latestModifiedUserId()) != 1) {
            throw new CantFindTargetFileException(
                    "The target file changed or no longer exists");
        }
    }

    @Override
    @Transactional
    public void restoreFromTrashBin(DeleteFileDTO deleteFileDTO) {
        File file = fileMapper.queryDeletedById(deleteFileDTO.id());
        if (file == null) {
            throw new CantFindTargetFileException(
                    "The target file does not exist in trash");
        }

        boolean canCurrentUserManage =
                permissionIntegration.checkPermissionByTypeTargetUserId(
                        PermissionTargetType.FILE.getCode(),
                        deleteFileDTO.id(),
                        deleteFileDTO.latestModifiedUserId(),
                        PermissionType.MANAGEABLE.getCode());
        if (!canCurrentUserManage) {
            throw new UserPermissionDeniedException(
                    "You don't have right to restore this file");
        }

        if (fileMapper.restoreFromTrashBin(
                deleteFileDTO.id(), deleteFileDTO.latestModifiedUserId()) != 1) {
            throw new CantFindTargetFileException(
                    "The target file changed or no longer exists in trash");
        }
    }

    @Override
    public File queryById(int id, int userId) {
        //this service function only user check detail of the file
        File file = fileMapper.queryById(id);
        if(file == null){
            throw new CantFindTargetFileException("The target file does not exist");
        }

        boolean canCurrentUserRead = permissionIntegration.checkPermissionByTypeTargetUserId(PermissionTargetType.FILE.getCode(),
                id, userId,PermissionType.READABLE.getCode());

        if(!canCurrentUserRead){
            throw new UserPermissionDeniedException("You don't have right to read this file");
        }

        //readerList是"读过这份文件的人"：不在名单里就追加；第一个读的人直接作为整个名单，不带逗号。
        //用parseIdList精确匹配，避免contains把3误判进"13,23"这种名单
        if (!parseIdList(file.getReaderList()).contains(userId)) {
            String readerList = file.getReaderList();
            if (readerList == null || readerList.isBlank()) {
                readerList = String.valueOf(userId);
            } else {
                readerList = readerList + "," + userId;
            }
            file.setReaderList(readerList);
            //专用语句只更新reader_list，阅读行为不应该改动recent_update_time和latest_modified_user_id
            fileMapper.updateReaderList(id, readerList);
        }
        return file;
    }

    @Override
    public List<File> queryByRepositoryId(int tenantId, int repositoryId) {
        //列表页只做展示，逐文件的读权限在打开文件(queryById)时校验，
        //避免列表一次拉N个文件就要调N次permission服务
        return fileMapper.queryByRepositoryId(tenantId, repositoryId);
    }

    @Override
    public List<File> queryAllTrashBin(int tenantId) {
        return fileMapper.queryAllTrashBin(tenantId);
    }

    @Override
    public void requestAccess(int id, int userId, String permissionType, long expectedExpirationTime) {
        File file = fileMapper.queryById(id);
        if(file == null){
            throw new CantFindTargetFileException("The target file does not exist");
        }
        User user = userMapper.queryById(userId);
        if(user == null){
            throw new UserNotExistException("The user is not exist in our system");
        }
        PermissionDTO permissionDTO = new PermissionDTO();

        permissionDTO.setUserId(userId);
        permissionDTO.setType(PermissionTargetType.FILE.getCode());
        permissionDTO.setTargetId(id);
        permissionDTO.setPermission(permissionType);
        permissionDTO.setExpirationTime(expectedExpirationTime);
        permissionIntegration.givePermissionByUserID(permissionDTO);
    }

    @Override
    public void inviteAccess(int id, int userId, String permissionType, long expectedExpirationTime, int currentUserId) {
        File file = fileMapper.queryById(id);
        if(file == null){
            throw new CantFindTargetFileException("The target file does not exist");
        }
        User user = userMapper.queryById(userId);
        if(user == null){
            throw new UserNotExistException("The user is not exist in our system");
        }

        boolean canCurrentUserManage = permissionIntegration.checkPermissionByTypeTargetUserId(PermissionTargetType.FILE.getCode(),
                id, currentUserId, PermissionType.MANAGEABLE.getCode());

        //邀请人没有manageable权限：降级为替被邀请人发起申请，等有权限的人审批
        if(!canCurrentUserManage){
            requestAccess(id, userId, permissionType, expectedExpirationTime);
            return;
        }

        //邀请人有manageable权限：直接授权并生效。
        //give是upsert且只置true不清false（邀请低级别不会把对方已有的高级别降掉），
        //但会把status重置为PENDING，所以补一次approve让邀请立即生效
        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setUserId(userId);
        permissionDTO.setType(PermissionTargetType.FILE.getCode());
        permissionDTO.setTargetId(id);
        permissionDTO.setPermission(permissionType);
        permissionDTO.setExpirationTime(expectedExpirationTime);
        permissionIntegration.givePermissionByUserID(permissionDTO);
        permissionIntegration.approvePermission(PermissionTargetType.FILE.getCode(), id, userId);
    }

    private File buildFile(AddFileDTO fileDTO){
        File file = new File();

        file.setRepositoryId(fileDTO.repositoryId());
        file.setOwnerId(fileDTO.ownerId());
        file.setTitle(fileDTO.title());
        file.setContent(fileDTO.content() == null ? "" : fileDTO.content());
        file.setWriterList(fileDTO.writableList());
        file.setReaderList(fileDTO.readableList());
        file.setManageableList(fileDTO.manageableList());
        file.setLatestModifiedUserId(fileDTO.latestModifiedUserId());
        file.setTenantId(fileDTO.tenantId());
        file.setPrivate(fileDTO.isPrivate());

        return file;
    }

    private File buildFile(UpdateFileDTO updateFileDTO, File file, String effectiveTitle){
        file.setTitle(effectiveTitle);
        file.setContent(updateFileDTO.content() == null ? "" : updateFileDTO.content());

        //already have writer
        String writerList = file.getWriterList();
        if (!parseIdList(writerList).contains(updateFileDTO.latestModifiedUserId())) {
            if (writerList == null || writerList.isBlank()) {
                writerList = String.valueOf(updateFileDTO.latestModifiedUserId());
            } else {
                writerList = writerList + "," + updateFileDTO.latestModifiedUserId();
            }
        }

        file.setWriterList(writerList);
        file.setReaderList(updateFileDTO.readableList());
        file.setManageableList(updateFileDTO.manageableList());
        file.setLatestModifiedUserId(updateFileDTO.latestModifiedUserId());
        file.setPrivate(updateFileDTO.isPrivate());
        return file;
    }



}
