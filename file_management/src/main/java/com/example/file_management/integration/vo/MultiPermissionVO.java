package com.example.file_management.integration.vo;

import com.example.file_management.controller.BaseVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MultiPermissionVO {
    private List<PermissionVO> permissionVOList;
    private BaseVO baseVO;
}
