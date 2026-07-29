package com.example.permission.controller;

import com.example.permission.Exception.PermissionNotFoundException;
import com.example.permission.Exception.PermissionTypeUnkownException;
import com.example.permission.controller.converter.PermissionVOConverter;
import com.example.permission.controller.dto.PermissionActionDTO;
import com.example.permission.controller.dto.PermissionDTO;
import com.example.permission.controller.vo.BaseVO;
import com.example.permission.controller.vo.MultiPermissionVO;
import com.example.permission.controller.vo.PermissionVO;
import com.example.permission.controller.vo.SinglePermissionVO;
import com.example.permission.entity.Permission;
import com.example.permission.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private static final Logger logger = LoggerFactory.getLogger(PermissionController.class);

    @Autowired
    PermissionService PermissionService;

    @PostMapping("/give")
    public BaseVO givePermission(@RequestBody PermissionDTO permissionDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            PermissionService.givePermission(permissionDTO.getUserId(), permissionDTO.getTargetId(),
                    permissionDTO.getType(), permissionDTO.getPermission(), permissionDTO.getExpirationTime());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(200, end - start, true, null);
        } catch (PermissionTypeUnkownException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(501, end - start, false, e.getMessage());
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(500, end - start, false, "other unknown error");
        }
    }

    @PostMapping("/approve")
    public BaseVO approvePermission(
            @RequestBody PermissionActionDTO permissionActionDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            PermissionService.approvePermission(
                    permissionActionDTO.getType(),
                    permissionActionDTO.getTargetId(),
                    permissionActionDTO.getUserId());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(200, end - start, true, null);
        } catch (PermissionTypeUnkownException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(501, end - start, false, e.getMessage());
        } catch (PermissionNotFoundException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(404, end - start, false, e.getMessage());
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(500, end - start, false, "other unknown error");
        }
    }

    @PostMapping("/reject")
    public BaseVO rejectPermission(
            @RequestBody PermissionActionDTO permissionActionDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            PermissionService.rejectPermission(
                    permissionActionDTO.getType(),
                    permissionActionDTO.getTargetId(),
                    permissionActionDTO.getUserId());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(200, end - start, true, null);
        } catch (PermissionTypeUnkownException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(501, end - start, false, e.getMessage());
        } catch (PermissionNotFoundException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(404, end - start, false, e.getMessage());
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(500, end - start, false, "other unknown error");
        }
    }

    @PostMapping("/revoke")
    public BaseVO revokePermission(
            @RequestBody PermissionActionDTO permissionActionDTO) {
        long start = System.currentTimeMillis();
        long end;
        try {
            PermissionService.revokePermission(
                    permissionActionDTO.getType(),
                    permissionActionDTO.getTargetId(),
                    permissionActionDTO.getUserId());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(200, end - start, true, null);
        } catch (PermissionTypeUnkownException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(501, end - start, false, e.getMessage());
        } catch (PermissionNotFoundException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(404, end - start, false, e.getMessage());
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(500, end - start, false, "other unknown error");
        }
    }
    @PostMapping("/batch_giving")
    public BaseVO batchGivingPermission(@RequestBody List<PermissionDTO> permissionDTOList) {
        long start = System.currentTimeMillis();
        long end;
        try {
            PermissionService.batchGivePermission(permissionDTOList);
            end = System.currentTimeMillis();
            return BaseVO.buildVO(200, end - start, true, null);
        } catch (PermissionTypeUnkownException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(501, end - start, false, e.getMessage());
        } catch (DuplicateKeyException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(503, end - start, false,
                    "duplicate permission: a record with the same type, targetId and userId already exists, nothing was inserted");
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(500, end - start, false, "other unknown error");
        }
    }

    @DeleteMapping("/target/{type}/{targetId}")
    public BaseVO deletePermissionsByTarget(@PathVariable String type, @PathVariable int targetId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            PermissionService.deletePermissionsByTarget(type, targetId);
            end = System.currentTimeMillis();
            return BaseVO.buildVO(200, end - start, true, null);
        } catch (PermissionTypeUnkownException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(501, end - start, false, e.getMessage());
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return BaseVO.buildVO(500, end - start, false, "other unknown error");
        }
    }

    @GetMapping("/{type}/{targetId}/{userId}")
    public SinglePermissionVO getPermission(@PathVariable String type, @PathVariable int targetId, @PathVariable int userId) {
        long start = System.currentTimeMillis();
        long end;
        SinglePermissionVO vo = new SinglePermissionVO();
        try {
            Permission permission = PermissionService.getPermission(type,targetId,userId);
            vo.setPermissionVO(PermissionVOConverter.convertToVO(permission));
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(200, end-start, true, null));
        } catch (PermissionNotFoundException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(404, end - start, false, e.getMessage()));
        } catch (PermissionTypeUnkownException e) {
            logger.warn(e.toString());
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(400, end - start, false, e.getMessage()));
        } catch (Exception e) {
            logger.error("failed to get permission", e);
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(500, end - start, false, "other unknown error"));
        }
        return vo;

    }

    @GetMapping("/target/{type}/{targetId}")
    public MultiPermissionVO getPermissionsByTarget(@PathVariable String type, @PathVariable int targetId) {
        long start = System.currentTimeMillis();
        long end;
        MultiPermissionVO vo = new MultiPermissionVO();
        try {
            List<Permission>  permissionList = PermissionService.getPermissionsByTarget(type,targetId);
            vo.setPermissionVOList(PermissionVOConverter.convertToVoList(permissionList));
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(200, end-start, true, null));
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(500, end - start, false, "other unknown error"));
        }
        return vo;

    }

    @GetMapping("/user/{userId}")
    public MultiPermissionVO getPermissionsByUserId(@PathVariable int userId) {
        long start = System.currentTimeMillis();
        long end;
        MultiPermissionVO vo = new MultiPermissionVO();
        try {
            List<Permission>  permissionList = PermissionService.getPermissionsByUserId(userId);
            vo.setPermissionVOList(PermissionVOConverter.convertToVoList(permissionList));
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(200, end-start, true, null));
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(500, end - start, false, "other unknown error"));
        }
        return vo;
    }

    @GetMapping("/user/{userId}/valid")
    public MultiPermissionVO getValidPermissionsByUserId(@PathVariable int userId) {
        long start = System.currentTimeMillis();
        long end;
        MultiPermissionVO vo = new MultiPermissionVO();
        try {
            List<Permission>  permissionList = PermissionService.getValidPermissionsByUserId(userId);
            vo.setPermissionVOList(PermissionVOConverter.convertToVoList(permissionList));
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(200, end-start, true, null));
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            vo.setBaseVO(BaseVO.buildVO(500, end - start, false, "other unknown error"));
        }
        return vo;
    }
}
