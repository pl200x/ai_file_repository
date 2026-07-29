package com.example.permission.entity;

import com.example.permission.enums.PermissionStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class Permission {

    private int id;
    //从一开始的单个file扩展到单一user对整个repository的权限。
    //通过enums来区分user获得的是哪一种权限。
    private String type;

    //目标对象的id
    private int targetId;

    private int userId;

    private Boolean readable = false;

    private Boolean writable = false;

    private Boolean manageable = false;

    private String status = PermissionStatus.PENDING.getCode();

    private Date permissionTime;

    private Date expirationTime;
}
