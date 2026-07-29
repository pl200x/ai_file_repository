package com.example.file_management.service;

import com.example.file_management.controller.dto.AddRepositoryDTO;
import com.example.file_management.entity.KnowledgeRepository;

import java.util.List;

public interface KnowledgeRepositoryService {
    void addRepository(AddRepositoryDTO addRepositoryDTO);
    KnowledgeRepository queryById(int id);
    List<KnowledgeRepository> queryByTenantId(int tenantId);
    List<KnowledgeRepository> queryByOwnerId(int ownerId);
    KnowledgeRepository queryPersonalByOwnerId(int ownerId);
    List<KnowledgeRepository> queryByMemberId(int tenantId, int memberId);
}
