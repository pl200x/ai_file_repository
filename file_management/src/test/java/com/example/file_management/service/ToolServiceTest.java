package com.example.file_management.service;

import com.example.file_management.agent.ChunkSearchResult;
import com.example.file_management.agent.toolservice.ToolService;
import com.example.file_management.controller.vo.ChunkVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolServiceTest {

    @Test
    void returnsOnlyReadableChunkContent() {
        ChunkService chunkService = mock(ChunkService.class);
        when(chunkService.queryTopKSimilarity(6, "access policy", 7))
                .thenReturn(List.of(
                        chunk(10, "guide.md", "readable content", 2, true),
                        chunk(11, "secret.md", "must not escape", 1, false),
                        chunk(12, null, null, 3, false)));
        ToolService toolService = toolService(chunkService, 6, mock(ChatClient.class));

        ChunkSearchResult result = toolService.topKSimilarity(
                "access policy",
                new ToolContext(Map.of("userId", 7)));

        assertEquals(1, result.readableMatchCount());
        assertEquals(2, result.hiddenMatchCount());
        assertEquals(1, result.chunks().size());
        assertEquals("guide.md", result.chunks().get(0).fileName());
        assertEquals("readable content", result.chunks().get(0).content());
        assertFalse(result.toString().contains("must not escape"));
        assertFalse(result.toString().contains("secret.md"));
    }

    @Test
    void takesIdentityFromToolContextAndUsesConfiguredTopK() {
        ChunkService chunkService = mock(ChunkService.class);
        when(chunkService.queryTopKSimilarity(8, "deployment guide", 19))
                .thenReturn(List.of());
        ToolService toolService = toolService(chunkService, 8, mock(ChatClient.class));

        toolService.topKSimilarity(
                "  deployment guide  ",
                new ToolContext(Map.of("userId", 19)));

        verify(chunkService).queryTopKSimilarity(8, "deployment guide", 19);
    }

    @Test
    void rejectsMissingUserIdentity() {
        ToolService toolService = toolService(
                mock(ChunkService.class), 6, mock(ChatClient.class));

        assertThrows(IllegalStateException.class, () -> toolService.topKSimilarity(
                "question",
                new ToolContext(Map.of())));
    }

    @Test
    void publishesExpectedCustomerSupportTools() {
        ToolService toolService = toolService(
                mock(ChunkService.class), 6, mock(ChatClient.class));

        ToolCallback[] callbacks = ToolCallbacks.from(toolService);
        Set<String> names = Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("topKSimilarity", "summarizeContent", "fileExtendAutomation"),
                names);
    }

    @Test
    void rejectsAnUnboundedTopKConfiguration() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        assertThrows(IllegalArgumentException.class, () ->
                new ToolService(mock(ChunkService.class), 51, builder));
    }

    @Test
    void summarizesOnlyReadableChunks() {
        ChunkService chunkService = mock(ChunkService.class);
        when(chunkService.queryTopKSimilarity(6, "总结 access-policy.md", 7))
                .thenReturn(List.of(
                        chunk(10, "access-policy.md", "读取权限需要所有者批准。", 1, true),
                        chunk(11, "secret.md", "不得出现在摘要中", 2, false)));
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("  读取权限需要所有者批准。  ");
        ToolService toolService = toolService(chunkService, 6, chatClient);

        String summary = toolService.summarizeContent(
                "总结 access-policy.md",
                new ToolContext(Map.of("userId", 7)));

        assertEquals("读取权限需要所有者批准。", summary);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        String summaryPrompt = promptCaptor.getValue();
        assertTrue(summaryPrompt.contains("access-policy.md"));
        assertTrue(summaryPrompt.contains("读取权限需要所有者批准。"));
        assertFalse(summaryPrompt.contains("secret.md"));
        assertFalse(summaryPrompt.contains("不得出现在摘要中"));
    }

    @Test
    void skipsSummaryModelWhenNoReadableChunksExist() {
        ChunkService chunkService = mock(ChunkService.class);
        when(chunkService.queryTopKSimilarity(6, "总结内部规范", 9))
                .thenReturn(List.of(
                        chunk(11, "secret.md", "hidden", 1, false)));
        ChatClient chatClient = mock(ChatClient.class);
        ToolService toolService = toolService(chunkService, 6, chatClient);

        String summary = toolService.summarizeContent(
                "总结内部规范",
                new ToolContext(Map.of("userId", 9)));

        assertEquals("未找到与需求相关且当前用户有权读取的内容，无法完成该操作。", summary);
        verify(chatClient, never()).prompt();
    }

    @Test
    void expandsOnlyReadableChunks() {
        ChunkService chunkService = mock(ChunkService.class);
        when(chunkService.queryTopKSimilarity(6, "扩写 access-policy.md", 7))
                .thenReturn(List.of(
                        chunk(10, "access-policy.md", "读取权限需要所有者批准。", 1, true),
                        chunk(11, "secret.md", "不得出现在扩写结果中", 2, false)));
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("  读取权限需要所有者批准，且需在审批系统中留痕。  ");
        ToolService toolService = toolService(chunkService, 6, chatClient);

        String expanded = toolService.fileExtendAutomation(
                "扩写 access-policy.md",
                new ToolContext(Map.of("userId", 7)));

        assertEquals("读取权限需要所有者批准，且需在审批系统中留痕。", expanded);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(promptCaptor.capture());
        String expandPrompt = promptCaptor.getValue();
        assertTrue(expandPrompt.contains("access-policy.md"));
        assertTrue(expandPrompt.contains("读取权限需要所有者批准。"));
        assertFalse(expandPrompt.contains("secret.md"));
        assertFalse(expandPrompt.contains("不得出现在扩写结果中"));
    }

    @Test
    void skipsExpandModelWhenNoReadableChunksExist() {
        ChunkService chunkService = mock(ChunkService.class);
        when(chunkService.queryTopKSimilarity(6, "扩写内部规范", 9))
                .thenReturn(List.of(
                        chunk(11, "secret.md", "hidden", 1, false)));
        ChatClient chatClient = mock(ChatClient.class);
        ToolService toolService = toolService(chunkService, 6, chatClient);

        String expanded = toolService.fileExtendAutomation(
                "扩写内部规范",
                new ToolContext(Map.of("userId", 9)));

        assertEquals("未找到与需求相关且当前用户有权读取的内容，无法完成该操作。", expanded);
        verify(chatClient, never()).prompt();
    }

    private ToolService toolService(
            ChunkService chunkService,
            int topK,
            ChatClient chatClient) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        return new ToolService(chunkService, topK, builder);
    }

    private ChunkVO chunk(
            int fileId,
            String fileName,
            String content,
            int chunkIndex,
            boolean visible) {
        ChunkVO chunk = new ChunkVO();
        chunk.setFileId(fileId);
        chunk.setFileName(fileName);
        chunk.setChunkContent(content);
        chunk.setChunkIndex(chunkIndex);
        chunk.setVisible(visible);
        return chunk;
    }
}
