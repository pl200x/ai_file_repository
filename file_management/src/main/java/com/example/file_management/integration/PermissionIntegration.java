package com.example.file_management.integration;

import com.example.file_management.controller.BaseVO;
import com.example.file_management.enums.PermissionStatus;
import com.example.file_management.integration.vo.MultiPermissionVO;
import com.example.file_management.integration.vo.PermissionVO;
import com.example.file_management.integration.vo.SinglePermissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class PermissionIntegration {
    @Autowired
    private RestTemplate restTemplate;


    @Value("${integration.permission.base-url:http://localhost:8086}")
    private String permissionBaseUrl;

    public boolean checkPermissionByTypeTargetUserId(
            String type,
            int targetId,
            int userId,
            String operateType) {

        String url = permissionBaseUrl
                + "/api/permissions" + "/{type}/{targetId}/{userId}";

        ResponseEntity<SinglePermissionVO> result =
                restTemplate.getForEntity(
                        url,
                        SinglePermissionVO.class,
                        type,
                        targetId,
                        userId
                );

        SinglePermissionVO body = result.getBody();

        if (body == null || body.getBaseVO() == null) {
            throw new RuntimeException("Failed to integrate with Permission System");
        }
        if (!body.getBaseVO().isSuccess()) {
            if (body.getBaseVO().getCode() == 404) {
                return false;
            }
            throw new RuntimeException("Failed to integrate with Permission System");
        }
        PermissionVO permissionVo = body.getPermissionVO();
        if (permissionVo == null) {
            return false;
        }
        if (!PermissionStatus.APPROVED.getCode().equals(permissionVo.getStatus())) {
            return false;
        }
        if (permissionVo.getExpirationTime() != null
                && !permissionVo.getExpirationTime().after(new java.util.Date())) {
            return false;
        }
        switch (operateType) {
            case "READABLE":
                return Boolean.TRUE.equals(permissionVo.getReadable());
            case "WRITABLE":
                return Boolean.TRUE.equals(permissionVo.getWritable());
            case "MANAGEABLE":
                return Boolean.TRUE.equals(permissionVo.getManageable());
            default:
                return false;
        }
    }

    public void givePermissionByUserID(PermissionDTO permissionDTO){
        String url = permissionBaseUrl
                + "/api/permissions/give";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PermissionDTO> httpEntity = new HttpEntity<>(permissionDTO, headers);
        ResponseEntity<BaseVO> result = restTemplate.postForEntity(url, httpEntity, BaseVO.class);
        if(result.getBody() == null || !result.getBody().isSuccess()){
            throw new RuntimeException("failed to give permission");
        }
    }

    public void batchGivingPermission(List<PermissionDTO> permissionDTOList){
        String url = permissionBaseUrl
                + "/api/permissions/batch_giving";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<PermissionDTO>> httpEntity = new HttpEntity<>(permissionDTOList, headers);
        ResponseEntity<BaseVO> result = restTemplate.postForEntity(url, httpEntity, BaseVO.class);
        if(result.getBody() == null || !result.getBody().isSuccess()){
            throw new RuntimeException("failed to give permission to all repository members");
        }
    }


    public void approvePermission(String type, int targetId, int userId){
        performPermissionAction(
                "/api/permissions/approve",
                new PermissionActionDTO(type, targetId, userId),
                "approve");
    }

    public void rejectPermission(String type, int targetId, int userId) {
        performPermissionAction(
                "/api/permissions/reject",
                new PermissionActionDTO(type, targetId, userId),
                "reject");
    }

    public void revokePermission(String type, int targetId, int userId) {
        performPermissionAction(
                "/api/permissions/revoke",
                new PermissionActionDTO(type, targetId, userId),
                "revoke");
    }

    public List<PermissionVO> getPermissionsByTarget(
            String type,
            int targetId) {
        String url = permissionBaseUrl
                + "/api/permissions/target/{type}/{targetId}";
        ResponseEntity<MultiPermissionVO> result =
                restTemplate.getForEntity(
                        url, MultiPermissionVO.class, type, targetId);
        MultiPermissionVO body = result.getBody();
        if (body == null
                || body.getBaseVO() == null
                || !body.getBaseVO().isSuccess()) {
            throw new RuntimeException(
                    "failed to query permissions of the target");
        }
        return body.getPermissionVOList() == null
                ? List.of()
                : body.getPermissionVOList();
    }

    public void deletePermissionsByTarget(String type, int targetId){
        String url = permissionBaseUrl
                + "/api/permissions/target/{type}/{targetId}";

        ResponseEntity<BaseVO> result = restTemplate.exchange(url, HttpMethod.DELETE, null, BaseVO.class, type, targetId);
        if(result.getBody() == null || !result.getBody().isSuccess()){
            throw new RuntimeException("failed to delete permissions of the target");
        }
    }

    private void performPermissionAction(
            String path,
            PermissionActionDTO permissionActionDTO,
            String action) {
        String url = permissionBaseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PermissionActionDTO> httpEntity =
                new HttpEntity<>(permissionActionDTO, headers);
        ResponseEntity<BaseVO> result =
                restTemplate.postForEntity(url, httpEntity, BaseVO.class);
        if (result.getBody() == null || !result.getBody().isSuccess()) {
            throw new RuntimeException(
                    "failed to " + action + " permission");
        }
    }

}
