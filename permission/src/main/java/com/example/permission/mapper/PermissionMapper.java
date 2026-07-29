package com.example.permission.mapper;

import com.example.permission.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface PermissionMapper {

    void insert(Permission Permission);

    void batchInsert(List<Permission> PermissionList);

    void deleteById(@Param("id") int id);

    void deleteByTypeAndTargetId(
            @Param("type") String type,
            @Param("targetId") int targetId
    );

    void updateById(Permission Permission);

    Permission selectById(@Param("id") int id);

    List<Permission> selectByType(
            @Param("type") String type,
            @Param("targetId") int targetId
    );

    Permission selectByTypeAndTargetIdAndUserId(
            @Param("type") String type,
            @Param("targetId") int targetId,
            @Param("userId") int userId
    );

    List<Permission> selectByUserId(@Param("userId") int userId);

    List<Permission> selectValidPermissionsByUserId(
            @Param("userId") int userId,
            @Param("currentTime") Date currentTime
    );

}
