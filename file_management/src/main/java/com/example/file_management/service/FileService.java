package com.example.file_management.service;

import com.example.file_management.controller.dto.AddFileDTO;
import com.example.file_management.controller.dto.DeleteFileDTO;
import com.example.file_management.controller.dto.FileVersionDTO;
import com.example.file_management.controller.dto.UpdateFileDTO;
import com.example.file_management.controller.vo.FileWriteResultVO;
import com.example.file_management.entity.File;

import java.util.List;


public interface FileService {
    FileWriteResultVO addFile(AddFileDTO fileDTO, FileVersionDTO fileVersionDTO);
    FileWriteResultVO updateFile(UpdateFileDTO updateFileDTO, FileVersionDTO fileVersionDTO);
    void deleteByFileId(DeleteFileDTO deleteFileDTO);
    void moveToTrashBin(DeleteFileDTO deleteFileDTO);
    void restoreFromTrashBin(DeleteFileDTO deleteFileDTO);
    File queryById(int id, int userId);
    List<File> queryByRepositoryId(int tenantId, int repositoryId);
    List<File> queryAllTrashBin(int tenantId);
    void requestAccess(int id,int userId,String permissionType, long expectedExpirationTime);
    void inviteAccess(int id,int userId,String permissionType, long expectedExpirationTime, int currentUserId);
}
