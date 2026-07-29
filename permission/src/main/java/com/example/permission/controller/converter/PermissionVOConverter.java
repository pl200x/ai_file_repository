package com.example.permission.controller.converter;

import com.example.permission.Exception.PermissionNotFoundException;
import com.example.permission.controller.vo.PermissionVO;
import com.example.permission.entity.Permission;
import com.example.permission.enums.PermissionStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PermissionVOConverter {
    public static PermissionVO convertToVO(Permission permission) {
        if (permission == null) {
            throw new PermissionNotFoundException(
                    "The requested permission does not exist");
        }

        PermissionVO permissionVO = new PermissionVO();
        permissionVO.setId(permission.getId());
        permissionVO.setType(permission.getType());
        permissionVO.setTargetId(permission.getTargetId());
        permissionVO.setUserId(permission.getUserId());
        permissionVO.setReadable(permission.getReadable());
        permissionVO.setWritable(permission.getWritable());
        permissionVO.setManageable(permission.getManageable());
        permissionVO.setStatus(permission.getStatus());
        permissionVO.setPermissionTime(permission.getPermissionTime());
        permissionVO.setExpirationTime(permission.getExpirationTime());
        return permissionVO;
    }
    public static List<PermissionVO> convertToVoList(List<Permission> permissionsList){
        List<PermissionVO> voList = new ArrayList<>();
        for(Permission permission : permissionsList){
            voList.add(convertToVO(permission));
        }
        return voList;
    }
}
