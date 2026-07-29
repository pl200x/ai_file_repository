package com.example.file_management.service;

import com.example.file_management.controller.dto.AddUserDTO;
import com.example.file_management.entity.User;

import java.util.List;

public interface UserService {
    void addUser(AddUserDTO addUserDTO);
    User queryById(int id);
    List<User> queryByIds(List<Integer> ids);
    User queryByEmail(String email);
    List<User> queryByTenantId(int tenantId);
    List<User> queryByGroupId(int groupId);
}
