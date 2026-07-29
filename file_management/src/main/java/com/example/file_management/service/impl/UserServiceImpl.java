package com.example.file_management.service.impl;

import com.example.file_management.controller.dto.AddUserDTO;
import com.example.file_management.entity.User;
import com.example.file_management.mapper.UserMapper;
import com.example.file_management.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public void addUser(AddUserDTO addUserDTO) {
        userMapper.addUser(buildUser(addUserDTO));
    }

    @Override
    public User queryById(int id) {
        return userMapper.queryById(id);
    }

    @Override
    public List<User> queryByIds(List<Integer> ids) {
        //空列表守卫放Java层不放XML：IN ()是SQL语法错误，空了直接返回不打DB
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return userMapper.queryByIds(ids);
    }

    @Override
    public User queryByEmail(String email) {
        return userMapper.queryByEmail(email);
    }

    @Override
    public List<User> queryByTenantId(int tenantId) {
        return userMapper.queryByTenantId(tenantId);
    }

    @Override
    public List<User> queryByGroupId(int groupId) {
        return userMapper.queryByGroupId(groupId);
    }

    private User buildUser(AddUserDTO addUserDTO) {
        User user = new User();
        user.setTenantId(addUserDTO.tenantId());
        user.setGroupId(addUserDTO.groupId());
        user.setName(addUserDTO.name());
        user.setEmail(addUserDTO.email());
        user.setProfile(addUserDTO.profile());
        return user;
    }
}
