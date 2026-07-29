package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.AddRepositoryDTO;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.mapper.KnowledgeRepositoryMapper;
import com.example.file_management.service.KnowledgeRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeRepositoryServiceImpl implements KnowledgeRepositoryService {
    @Autowired
    private KnowledgeRepositoryMapper knowledgeRepositoryMapper;

    @Override
    public void addRepository(AddRepositoryDTO addRepositoryDTO) {
        knowledgeRepositoryMapper.addRepository(buildRepository(addRepositoryDTO));
    }

    @Override
    public KnowledgeRepository queryById(int id) {
        return knowledgeRepositoryMapper.queryById(id);
    }

    @Override
    public List<KnowledgeRepository> queryByTenantId(int tenantId) {
        return knowledgeRepositoryMapper.queryByTenantId(tenantId);
    }

    @Override
    public List<KnowledgeRepository> queryByOwnerId(int ownerId) {
        return knowledgeRepositoryMapper.queryByOwnerId(ownerId);
    }

    @Override
    public KnowledgeRepository queryPersonalByOwnerId(int ownerId) {
        return knowledgeRepositoryMapper.queryPersonalByOwnerId(ownerId);
    }

    @Override
    public List<KnowledgeRepository> queryByMemberId(int tenantId, int memberId) {
        return knowledgeRepositoryMapper.queryByMemberId(tenantId, memberId);
    }

    private KnowledgeRepository buildRepository(AddRepositoryDTO addRepositoryDTO) {
        KnowledgeRepository repository = new KnowledgeRepository();
        repository.setTenantId(addRepositoryDTO.tenantId());
        repository.setOwnerId(addRepositoryDTO.ownerId());
        repository.setTitle(addRepositoryDTO.title());
        repository.setDescription(addRepositoryDTO.description());
        repository.setWritableList(addRepositoryDTO.writableList());
        repository.setReadableList(addRepositoryDTO.readableList());
        repository.setManageableList(addRepositoryDTO.manageableList());
        repository.setPersonal(addRepositoryDTO.isPersonal());
        return repository;
    }
}
