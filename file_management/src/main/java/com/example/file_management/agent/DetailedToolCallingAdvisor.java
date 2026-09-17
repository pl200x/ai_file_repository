package com.example.file_management.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 将模型/工具循环显式化并限制最大轮次。日志只保留执行元数据，
 * 不记录用户问题、工具参数、Chunk 正文或模型答案。
 */
public class DetailedToolCallingAdvisor extends ToolCallingAdvisor {

    private static final Logger log = LoggerFactory.getLogger(DetailedToolCallingAdvisor.class);

    private final int maxIterations;

    public DetailedToolCallingAdvisor(ToolCallingManager toolCallingManager, int maxIterations) {
        super(toolCallingManager,
                chatResponse -> chatResponse != null && chatResponse.hasToolCalls(),
                DEFAULT_ORDER,
                true);
        Assert.isTrue(maxIterations > 0, "maxIterations must be greater than 0");
        this.maxIterations = maxIterations;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Assert.notNull(request, "request must not be null");
        Assert.notNull(chain, "chain must not be null");

        if (!(request.prompt().getOptions() instanceof ToolCallingChatOptions options)) {
            return chain.nextCall(request);
        }

        List<Message> instructions = request.prompt().getInstructions();

        for (int iteration = 1; iteration <= this.maxIterations; iteration++) {
            log.info("FileAgent iteration {}/{}", iteration, this.maxIterations);

            ChatClientRequest iterationRequest = ChatClientRequest.builder()
                    .prompt(new Prompt(instructions, options))
                    .context(request.context())
                    .build();

            ChatClientResponse clientResponse = chain.copy(this).nextCall(iterationRequest);
            ChatResponse chatResponse = clientResponse.chatResponse();
            Assert.notNull(chatResponse, "chatResponse must not be null");

            printModelResponse(chatResponse);

            if (!chatResponse.hasToolCalls()) {
                log.info("FileAgent completed at iteration {}", iteration);
                return clientResponse;
            }

            if (iteration == this.maxIterations) {
                String message = "无法在工具调用轮次限制内完成本次请求。";
                log.warn("FileAgent reached the tool-call iteration limit: {}",
                        this.maxIterations);
                ChatResponse limitedResponse = ChatResponse.builder()
                        .from(chatResponse)
                        .generations(List.of(new Generation(new AssistantMessage(message))))
                        .build();
                return clientResponse.mutate().chatResponse(limitedResponse).build();
            }

            ToolExecutionResult executionResult = this.toolCallingManager
                    .executeToolCalls(iterationRequest.prompt(), chatResponse);
            printToolResults(executionResult);

            if (executionResult.returnDirect()) {
                ChatResponse directResponse = ChatResponse.builder()
                        .from(chatResponse)
                        .generations(ToolExecutionResult.buildGenerations(executionResult))
                        .build();
                return clientResponse.mutate().chatResponse(directResponse).build();
            }

            instructions = executionResult.conversationHistory();
        }

        throw new IllegalStateException("Unexpected end of FileAgent iteration loop");
    }

    private void printModelResponse(ChatResponse chatResponse) {
        for (Generation generation : chatResponse.getResults()) {
            AssistantMessage output = generation.getOutput();
            log.debug("Model response: finishReason={}, hasText={}, toolCallCount={}",
                    generation.getMetadata().getFinishReason(),
                    output.getText() != null && !output.getText().isBlank(),
                    output.getToolCalls().size());

            for (AssistantMessage.ToolCall toolCall : output.getToolCalls()) {
                log.debug("Tool requested: name={}, id={}, type={}",
                        toolCall.name(), toolCall.id(), toolCall.type());
            }
        }
    }

    private void printToolResults(ToolExecutionResult executionResult) {
        for (Message message : executionResult.conversationHistory()) {
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                    log.debug("Tool completed: name={}, id={}",
                            response.name(), response.id());
                }
            }
        }
    }
}
