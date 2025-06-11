package cn.apmen.jsagent.framework.execution;

import cn.apmen.jsagent.framework.core.RunnerContext;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.lifecycle.AgentLifecycleManager;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.AgentToolResponse;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import cn.apmen.jsagent.framework.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具调用处理器
 * 负责处理所有工具调用相关的逻辑，从AgentRunner中分离出来
 */
@Slf4j
public class ToolCallProcessor {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentLifecycleManager lifecycleManager;
    public ToolCallProcessor(AgentLifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
    }
    /**
     * 处理工具调用
     * @param toolCalls 工具调用列表
     * @param context 运行上下文
     * @param toolRegistry 工具注册器
     * @param agentId Agent ID
     * @return 处理结果，true表示继续循环，false表示终止循环
     */
    public Mono<ToolCallResult> processToolCalls(List<ToolCall> toolCalls,
                                                RunnerContext context,
                                                ToolRegistry toolRegistry,
                                                String agentId) {
        if (toolRegistry == null) {
            log.error("Tool registry not available");
            return Mono.error(new AgentException(
                ErrorCode.SYSTEM_ERROR,
                "Tool registry not available for agent: " + agentId));
        }

        // 检查轮次限制
        if (context.isMaxRoundsReached()) {
            return Mono.error(new AgentException(
                ErrorCode.CONTEXT_MAX_ROUNDS_EXCEEDED,
                "Maximum rounds exceeded: " + context.getCurrentRound() + "/" + context.getMaxRounds()));
        }

        // 执行生命周期钩子 - 工具调用前
        return lifecycleManager.executeBeforeToolCall(context)
                .then(Mono.defer(() -> executeToolCalls(toolCalls, context, toolRegistry, agentId)))
                .flatMap(result -> lifecycleManager.executeAfterToolCall(context)
                    .thenReturn(result))
                .onErrorResume(error -> lifecycleManager.executeError(context, error)
                    .then(Mono.error(error)));
    }
    /**
     * 执行工具调用
     */
    private Mono<ToolCallResult> executeToolCalls(List<ToolCall> toolCalls,
                                                 RunnerContext context,
                                                 ToolRegistry toolRegistry,
                                                 String agentId) {
        // 创建ToolContext
        ToolContext toolContext = ToolContext.builder()
                .runnerContext(context)
                .currentRound(context.getCurrentRound())
                .build();

        // 执行所有工具调用
        List<Mono<ToolResult>> toolExecutions = toolCalls.stream()
                .map(toolCall -> toolRegistry.execute(toolCall, toolContext)
                    .onErrorMap(error -> new AgentException(
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "Tool execution failed: " + toolCall.getFunction().getName(), error)))
                .collect(Collectors.toList());

        return Flux.fromIterable(toolExecutions)
                .flatMap(mono -> mono)
                .collectList()
                .flatMap(results -> processToolResults(results, context));
    }
    /**
     * 处理工具调用结果
     */
    private Mono<ToolCallResult> processToolResults(List<ToolResult> results, RunnerContext context) {
        // 检查是否有直接输出的Agent调用
        for (ToolResult result : results) {
            if (isAgentDirectOutput(result)) {
                // 处理Agent直接输出
                return handleAgentDirectOutput(result, context)
                        .thenReturn(ToolCallResult.terminate()); // 返回终止循环
            }
        }

        // 普通工具调用处理
        boolean hasSuccessfulToolCall = false;
        for (ToolResult result : results) {
            String content = result.isSuccess() ? result.getContent() : result.getError();
            context.addToolMessage(result.getToolCallId(), content);

            // 记录是否有成功的工具调用
            if (result.isSuccess()) {
                hasSuccessfulToolCall = true;
            }
        }

        // 只有在有成功的工具调用时才递增轮次
        if (hasSuccessfulToolCall) {
            int newRound = context.incrementRound();
            log.debug("Tool calls completed successfully, continuing to round {}", newRound);
            return Mono.just(ToolCallResult.continueLoop());
        } else {
            log.warn("All tool calls failed, not incrementing round. Current round: {}", context.getCurrentRound());
            return Mono.just(ToolCallResult.continueLoop());
        }
    }
    /**
     * 检查是否为Agent直接输出
     */
    private boolean isAgentDirectOutput(ToolResult result) {
        return result.isSuccess() &&
               result.getContent() != null &&
               result.getContent().startsWith("AGENT_DIRECT_OUTPUT:");
    }
    /**
     * 处理Agent直接输出
     */
    private Mono<Void> handleAgentDirectOutput(ToolResult result, RunnerContext context) {
        try {
            // 解析AgentToolResponse
            String jsonContent = result.getContent().substring("AGENT_DIRECT_OUTPUT:".length());
            AgentToolResponse agentResponse = objectMapper.readValue(jsonContent, AgentToolResponse.class);

            log.info("Agent direct output detected: {}", agentResponse.getCallSummary());

            // 标记上下文为直接输出模式
            context.addSystemMessage("Agent " + agentResponse.getTargetAgentName() +
                " provided direct output to user. Conversation flow interrupted.");

            return Mono.empty();

        } catch (Exception e) {
            log.error("Failed to handle agent direct output", e);
            return Mono.empty();
        }
    }
    /**
     * 工具调用结果
     */
    public static class ToolCallResult {
        private final boolean shouldContinue;
        private ToolCallResult(boolean shouldContinue) {
            this.shouldContinue = shouldContinue;
        }
        public static ToolCallResult continueLoop() {
            return new ToolCallResult(true);
        }
        public static ToolCallResult terminate() {
            return new ToolCallResult(false);
        }
        public boolean shouldContinue() {
            return shouldContinue;
        }
    }
}