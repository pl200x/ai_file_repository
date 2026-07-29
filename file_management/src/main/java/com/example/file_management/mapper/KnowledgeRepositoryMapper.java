package com.example.file_management.mapper;

import com.example.file_management.entity.KnowledgeRepository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeRepositoryMapper {
    void addRepository(KnowledgeRepository repository);
    KnowledgeRepository queryById(int id);
    KnowledgeRepository queryByIdForUpdate(int id);
    List<KnowledgeRepository> queryByTenantId(int tenantId);
    List<KnowledgeRepository> queryByOwnerId(int ownerId);
    KnowledgeRepository queryPersonalByOwnerId(int ownerId);
    List<KnowledgeRepository> queryByMemberId(
            @Param("tenantId") int tenantId,
            @Param("memberId") int memberId
    );
    void updateMemberList(
            @Param("id") long id,
            @Param("writableList") String writableList,
            @Param("readableList") String readableList,
            @Param("manageableList") String manageableList
    );
}
