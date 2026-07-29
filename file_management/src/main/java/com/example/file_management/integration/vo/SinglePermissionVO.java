package com.example.file_management.integration.vo;

import com.example.file_management.controller.BaseVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SinglePermissionVO {
    private BaseVO baseVO;
    private PermissionVO permissionVO;
}
