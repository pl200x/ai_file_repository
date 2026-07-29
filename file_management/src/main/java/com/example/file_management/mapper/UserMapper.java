package com.example.file_management.mapper;

import com.example.file_management.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    void addUser(User user);
    User queryById(int id);
    List<User> queryByIds(@Param("ids") List<Integer> ids);
    User queryByEmail(String email);
    List<User> queryByTenantId(int tenantId);
    List<User> queryByGroupId(int groupId);
}
