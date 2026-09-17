package com.example.file_management.controller;

import com.example.file_management.agent.FileAgent;
import com.example.file_management.controller.vo.DataVO;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentControllerTest {

    @Test
    void passesNormalizedRequestAndCurrentUserToAgent() {
        AtomicReference<String> session = new AtomicReference<>();
        AtomicReference<String> question = new AtomicReference<>();
        AtomicInteger user = new AtomicInteger();
        FileAgent agent = (sessionId, userInput, userId) -> {
            session.set(sessionId);
            question.set(userInput);
            user.set(userId);
            return "answer";
        };
        FileAgentController controller = new FileAgentController(agent);

        DataVO<String> response = controller.chat(
                " session-1 ", " question ", 7);

        assertTrue(response.isSuccess());
        assertEquals("answer", response.getData());
        assertEquals("session-1", session.get());
        assertEquals("question", question.get());
        assertEquals(7, user.get());
    }

    @Test
    void rejectsInvalidInputBeforeCallingAgent() {
        FileAgent agent = (sessionId, userInput, userId) -> {
            throw new AssertionError("agent must not be called");
        };
        FileAgentController controller = new FileAgentController(agent);

        DataVO<String> response = controller.chat("session-1", " ", 7);

        assertFalse(response.isSuccess());
        assertEquals(400, response.getCode());
    }

    @Test
    void hidesInternalFailuresFromApiResponse() {
        FileAgent agent = (sessionId, userInput, userId) -> {
            throw new IllegalStateException("sensitive upstream detail");
        };
        FileAgentController controller = new FileAgentController(agent);

        DataVO<String> response = controller.chat("session-1", "question", 7);

        assertFalse(response.isSuccess());
        assertEquals(500, response.getCode());
        assertEquals("Unable to complete the chat request", response.getErrorMessage());
    }
}
