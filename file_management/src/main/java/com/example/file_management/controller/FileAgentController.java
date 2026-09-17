package com.example.file_management.controller;

import com.example.file_management.agent.FileAgent;
import com.example.file_management.controller.vo.DataVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/agent")
public class FileAgentController {

    private static final Logger log = LoggerFactory.getLogger(FileAgentController.class);
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final int MAX_USER_INPUT_LENGTH = 1_000;

    private final FileAgent fileAgent;

    public FileAgentController(FileAgent fileAgent) {
        this.fileAgent = fileAgent;
    }

    @PostMapping("/chat")
    public DataVO<String> chat(
            @RequestParam String sessionId,
            @RequestParam String userInput,
            @RequestParam int userId) {
        long startedAt = System.currentTimeMillis();
        String normalizedSessionId = sessionId == null ? "" : sessionId.trim();
        String normalizedInput = userInput == null ? "" : userInput.trim();

        if (normalizedSessionId.isEmpty()
                || normalizedSessionId.length() > MAX_SESSION_ID_LENGTH) {
            return DataVO.buildDataVO(
                    400, elapsed(startedAt), false,
                    "sessionId must contain 1 to 128 characters", null);
        }
        if (normalizedInput.isEmpty()
                || normalizedInput.length() > MAX_USER_INPUT_LENGTH) {
            return DataVO.buildDataVO(
                    400, elapsed(startedAt), false,
                    "userInput must contain 1 to 1000 characters", null);
        }
        if (userId <= 0) {
            return DataVO.buildDataVO(
                    400, elapsed(startedAt), false,
                    "userId must be greater than 0", null);
        }

        try {
            String answer = fileAgent.chat(
                    normalizedSessionId,
                    normalizedInput,
                    userId);
            return DataVO.buildDataVO(
                    200, elapsed(startedAt), true, null, answer);
        } catch (Exception exception) {
            log.error("event=FILE_AGENT_CHAT_ERROR sessionId={} userId={} error={}",
                    normalizedSessionId, userId, exception.getMessage(), exception);
            return DataVO.buildDataVO(
                    500, elapsed(startedAt), false,
                    "Unable to complete the chat request", null);
        }
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }
}
