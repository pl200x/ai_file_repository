package com.example.file_management.controller;

import com.example.file_management.controller.vo.ChunkVO;
import com.example.file_management.controller.vo.MultiDataVO;
import com.example.file_management.service.ChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chunk")
public class ChunkController {
    private static final Logger logger = LoggerFactory.getLogger(ChunkController.class);

    @Autowired
    private ChunkService chunkService;

    //默认4条与Spring AI的SearchRequest.DEFAULT_TOP_K一致；上限在service层校验
    @GetMapping("/query")
    public MultiDataVO<ChunkVO> queryTopKSimilarity(
            @RequestParam String userInput,
            @RequestParam int userId,
            @RequestParam(defaultValue = "4") int k) {
        long start = System.currentTimeMillis();
        long end;
        try {
            List<ChunkVO> chunkVOList =
                    chunkService.queryTopKSimilarity(k, userInput, userId);
            end = System.currentTimeMillis();
            return MultiDataVO.buildDataVO(200, end - start, true, null, chunkVOList);
        } catch (IllegalArgumentException e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return MultiDataVO.buildDataVO(400, end - start, false, e.getMessage(), null);
        } catch (Exception e) {
            logger.error(e.toString());
            end = System.currentTimeMillis();
            return MultiDataVO.buildDataVO(500, end - start, false, "other unknown error", null);
        }
    }
}
