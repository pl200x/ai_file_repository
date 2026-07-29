package com.example.permission.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@NoArgsConstructor
@Setter
@AllArgsConstructor
public class MultiPermissionVO {
    private List<PermissionVO> permissionVOList;
    private BaseVO baseVO;
}
