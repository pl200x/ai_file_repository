package com.example.file_management.controller;

import com.example.file_management.controller.vo.DataVO;
import com.example.file_management.entity.KnowledgeRepository;
import com.example.file_management.service.KnowledgeRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/repository")
public class KnowledgeRepositoryController {
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRepositoryController.class);
    @Autowired
    private KnowledgeRepositoryService knowledgeRepositoryService;

    @GetMapping("/list")
    public DataVO<List<KnowledgeRepository>> queryByTenantId(@RequestParam int tenantId) {
        long start = System.currentTimeMillis();
        long end;
        try {
            List<KnowledgeRepository> repositories = knowledgeRepositoryService.queryByTenantId(tenantId);
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(200, end - start, true, null, repositories);
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return DataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }
}
