package cn.apmen.jsagent.framework.execution;

import cn.apmen.jsagent.framework.core.AgentEvent;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.core.RunnerContext;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.lifecycle.AgentLifecycleManager;
import cn.apmen.jsagent.framework.plugin.PluginManager;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import cn.apmen.jsagent.framework.stream.StreamBuffer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Agent执行器
 * 重构后的执行引擎，解耦并集成生命周期管理和插件系统
 */
@Slf4j
public class AgentExecutor {
    private final AgentLifecycleManager lifecycleManager;
    private final PluginManager pluginManager;
    private final ToolCallProcessor toolCallProcessor;
    private final ContextBuilder contextBuilder;
    public AgentExecutor(AgentLifecycleManager lifecycleManager,
                        PluginManager pluginManager,
                        ToolCallProcessor toolCallProcessor,
                        ContextBuilder contextBuilder) {
        this.lifecycleManager = lifecycleManager;
        this.pluginManager = pluginManager;
        this.toolCallProcessor = toolCallProcessor;
        this.contextBuilder = contextBuilder;
    }
    /**
     * 流式执行Agent
     */
    public Flux<AgentEvent> executeStream(CoreAgent agent, UserChatRequest request) {
        String executionId = UUID.randomUUID().toString();
        return Mono.fromCallable(() -> contextBuilder.buildContext(request, agent.getId(), executionId))
                .flatMapMany(context -> executeStreamWithLifecycle(agent, context))
                .map(this::convertToAgentEvent)
                .doOnNext(event -> log.debug("Agent event generated: {}", event))
                .onErrorMap(this::mapToAgentException)
                .onErrorResume(throwable -> Flux.just(createErrorEvent(throwable)));
    }
    /**
     * 非流式执行Agent
     */
    public Mono<AgentEvent> execute(CoreAgent agent, UserChatRequest request) {
        String executionId = UUID.randomUUID().toString();
        return Mono.fromCallable(() -> contextBuilder.buildContext(request, agent.getId(), executionId))
                .flatMap(context -> executeWithLifecycle(agent, context))
                .map(this::convertToAgentEvent)
                .doOnNext(event -> log.debug("Agent event generated: {}", event))
                .onErrorMap(this::mapToAgentException)
                .onErrorResume(throwable -> Mono.just(createErrorEvent(throwable)));
    }
    /**
     * 带生命周期的流式执行
     */
    private Flux<AgentResponse> executeStreamWithLifecycle(CoreAgent agent, RunnerContext context) {
        return lifecycleManager.executeInitialize(context)
                .then(lifecycleManager.executeBeforeExecution(context))
                .thenMany(executeStreamLoop(agent, context))
                .doOnComplete(() -> lifecycleManager.executeComplete(context).subscribe())
                .doOnError(error -> lifecycleManager.executeError(context, error).subscribe());
    }
    /**
     * 带生命周期的非流式执行
     */
    private Mono<AgentResponse> executeWithLifecycle(CoreAgent agent, RunnerContext context) {
        return lifecycleManager.executeInitialize(context)
                .then(lifecycleManager.executeBeforeExecution(context))
                .then(executeLoop(agent, context))
                .doOnSuccess(response -> lifecycleManager.executeComplete(context).subscribe())
                .doOnError(error -> lifecycleManager.executeError(context, error).subscribe());
    }
    /**
     * 流式执行循环
     */
    private Flux<AgentResponse> executeStreamLoop(CoreAgent agent, RunnerContext context) {
        return Flux.defer(() -> {
            if (context.isMaxRoundsReached()) {
                return Flux.just(AgentResponse.error("Maximum rounds reached"));
            }
            return executeStreamRoundWithBuffer(agent, context);
        });
    }
    /**
     * 执行单轮流式调用，使用智能缓冲
     */
    private Flux<AgentResponse> executeStreamRoundWithBuffer(CoreAgent agent, RunnerContext context) {
        // 默认启用流式输出工具调用内容
        StreamBuffer buffer = new StreamBuffer(true);
        return agent.runStream(context)
                .flatMap(response -> {
                    // 执行生命周期钩子
                    return lifecycleManager.executeAfterExecution(context, response)
                            .thenReturn(response);
                })
                .flatMap(response -> {
                    StreamBuffer.BufferDecision decision = buffer.addResponse(response);
                    switch (decision) {
                        case CONTINUE_BUFFERING:
                            return Flux.empty();
                        case DIRECT_OUTPUT:
                            return Flux.just(response);
                        case RELEASE_ALL:
                            if (buffer.isToolCallDetected()) {
                                return handleToolCallsInStream(buffer.getBufferedResponses(), agent, context);
                            } else {
                                return Flux.fromIterable(buffer.getBufferedResponses());
                            }
                        default:
                            return Flux.empty();
                    }
                })
                .concatWith(Flux.defer(() -> {
                    if (buffer.isToolCallDetected() && !buffer.isStreamCompleted()) {
                        return handleToolCallsInStream(buffer.getBufferedResponses(), agent, context);
                    }
                    return Flux.empty();
                }));
    }
    /**
     * 非流式执行循环
     */
    private Mono<AgentResponse> executeLoop(CoreAgent agent, RunnerContext context) {
        return executeRound(agent, context)
                .expand(response -> {
                    if (response.getType() == AgentResponse.ResponseType.TOOL_CALL &&
                        !context.isMaxRoundsReached()) {
                        return toolCallProcessor.processToolCalls(
                                response.getToolCalls(), context, agent.getToolRegistry(), agent.getId())
                                .flatMap(result -> {
                                    if (result.shouldContinue()) {
                                        return executeRound(agent, context);
                                    } else {
                                        log.debug("Direct output detected, terminating loop");
                                        return Mono.just(AgentResponse.text("Direct output terminated", true));
                                    }
                                });
                    }
                    return Mono.empty();
                })
                .filter(response -> response.getType() != AgentResponse.ResponseType.TOOL_CALL)
                .last();
    }
    /**
     * 执行单轮调用
     */
    private Mono<AgentResponse> executeRound(CoreAgent agent, RunnerContext context) {
        return agent.run(context)
                .flatMap(response -> lifecycleManager.executeAfterExecution(context, response)
                    .thenReturn(response));
    }
    /**
     * 处理流式响应中的工具调用
     */
    private Flux<AgentResponse> handleToolCallsInStream(List<AgentResponse> responses,
                                                       CoreAgent agent,
                                                       RunnerContext context) {
        AgentResponse toolCallResponse = responses.stream()
                .filter(r -> r.getType() == AgentResponse.ResponseType.TOOL_CALL)
                .findFirst()
                .orElse(null);
        if (toolCallResponse != null) {
            return toolCallProcessor.processToolCalls(
                    toolCallResponse.getToolCalls(), context, agent.getToolRegistry(), agent.getId())
                    .flatMapMany(result -> {
                        if (result.shouldContinue()) {
                            return executeStreamLoop(agent, context);
                        } else {
                            log.debug("Direct output detected, terminating stream loop");
                            return Flux.empty();
                        }
                    });
        } else {
            return Flux.fromIterable(responses);
        }
    }
    /**
     * 将AgentResponse转换为AgentEvent
     */
    private AgentEvent convertToAgentEvent(AgentResponse response) {
        switch (response.getType()) {
            case TEXT:
                return AgentEvent.textResponse(response.getContent(), response.isFinalResponse());
            case TOOL_CALL:
                return AgentEvent.toolCall(response.getToolCalls());
            case ERROR:
                return AgentEvent.error(response.getError());
            case THINKING:
                return AgentEvent.thinking(response.getContent());
            default:
                return AgentEvent.error("Unknown response type");
        }
    }
    /**
     * 将异常映射为AgentException
     */
    private Throwable mapToAgentException(Throwable throwable) {
        if (throwable instanceof AgentException) {
            return throwable;
        }
        if (throwable instanceof IllegalArgumentException) {
            return new AgentException(
                ErrorCode.CONFIG_INVALID,
                "Invalid configuration or arguments", throwable);
        }
        if (throwable instanceof NullPointerException) {
            return new AgentException(
                ErrorCode.CONTEXT_INVALID_STATE,
                "Invalid context state", throwable);
        }
        return new AgentException(
            ErrorCode.SYSTEM_ERROR,
            "Unexpected error in AgentExecutor", throwable);
    }
    /**
     * 创建错误事件
     */
    private AgentEvent createErrorEvent(Throwable throwable) {
        if (throwable instanceof AgentException) {
            AgentException agentException = (AgentException) throwable;
            log.error("Agent execution failed: [{}] {}",
                agentException.getErrorCode().getCode(),
                agentException.getMessage(),
                agentException);
            if (agentException.isUserError()) {
                return AgentEvent.error(agentException.getMessage());
            } else {
                return AgentEvent.error("系统内部错误，请稍后重试");
            }
        }
        log.error("Unexpected error in agent execution", throwable);
        return AgentEvent.error("Agent execution failed");
    }
}