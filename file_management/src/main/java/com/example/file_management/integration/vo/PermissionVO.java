package com.example.file_management.integration.vo;

import com.example.file_management.enums.PermissionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PermissionVO {
    private int id;
    private String type;
    private int targetId;

    private int userId;

    private Boolean readable = false;

    private Boolean writable = false;

    private Boolean manageable = false;

    private String status = PermissionStatus.PENDING.getCode();
    private Date permissionTime;

    private Date expirationTime;

}
