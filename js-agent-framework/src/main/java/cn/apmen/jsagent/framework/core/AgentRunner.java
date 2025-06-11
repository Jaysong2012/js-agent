package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.conversation.ConversationMetadata;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import cn.apmen.jsagent.framework.stream.StreamBuffer;
import cn.apmen.jsagent.framework.tool.AgentToolResponse;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 实现Agent Loop
 * 负责构建RunnerContext并控制整个Run Loop，包括工具调用循环
 */
@Slf4j
public class AgentRunner {

    private final CoreAgent agent;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 注入ConversationService
    private final ConversationService conversationService;

    public AgentRunner(CoreAgent agent, AgentConfig agentConfig,
                       ConversationService conversationService) {
        this.agent = agent;
        this.agentConfig = agentConfig;
        this.conversationService = conversationService;
    }

    /**
     * 流式运行Agent
     * @param request 用户聊天请求
     * @return Agent事件流
     */
    public Flux<AgentEvent> runStream(UserChatRequest request) {
        return Mono.fromCallable(() -> buildRunnerContext(request))
                .flatMapMany(this::executeStreamLoop)
                .map(this::convertToAgentEvent)
                .doOnNext(event -> log.debug("Agent event generated: {}", event))
                .onErrorMap(this::mapToAgentException)
                .onErrorResume(throwable -> Flux.just(createErrorEvent(throwable)));
    }

    /**
     * 非流式运行Agent
     * @param request 用户聊天请求
     * @return Agent事件
     */
    public Mono<AgentEvent> run(UserChatRequest request) {
        return Mono.fromCallable(() -> buildRunnerContext(request))
                .flatMap(this::executeNonStreamLoop)
                .map(this::convertToAgentEvent)
                .doOnNext(event -> log.debug("Agent event generated: {}", event))
                .onErrorMap(this::mapToAgentException)
                .onErrorResume(throwable -> Mono.just(createErrorEvent(throwable)));
    }

    /**
     * 构建RunnerContext
     */
    private RunnerContext buildRunnerContext(UserChatRequest request) {
        try {
            // 验证请求参数
            if (request == null) {
                throw new AgentException(ErrorCode.CONFIG_INVALID,
                    "UserChatRequest cannot be null");
            }

            if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
                throw new AgentException(ErrorCode.CONFIG_INVALID,
                    "User ID cannot be null or empty");
            }

            if (request.getConversationId() == null || request.getConversationId().trim().isEmpty()) {
                throw new AgentException(ErrorCode.CONFIG_INVALID,
                    "Conversation ID cannot be null or empty");
            }

            RunnerContext.RunnerContextBuilder builder = RunnerContext.builder()
                    .conversationService(conversationService) // 注入ConversationService
                    .userId(request.getUserId())
                    .conversationId(request.getConversationId())
                    .systemPrompt(buildSystemPrompt());

            // 设置上下文token限制
            if (agentConfig != null && agentConfig.getMaxContextTokens() != null) {
                builder.maxContextTokens(agentConfig.getMaxContextTokens());
            }

            // 添加用户消息到本地历史（ConversationService会自动同步）
            if (request.getMessage() != null && request.getMessage().getMessage() != null) {
                builder.localMessageHistory(List.of(
                        new Message("user", request.getMessage().getMessage())
                ));
            }

            // 从配置中设置最大轮次
            if (agentConfig != null && agentConfig.getMaxRounds() != null) {
                builder.maxRounds(agentConfig.getMaxRounds());
            }

            RunnerContext context = builder.build();

            // 验证构建的上下文
            if (!context.isValid()) {
                throw new AgentException(
                    ErrorCode.CONTEXT_INVALID_STATE,
                    "Built context is invalid");
            }

            // 初始化会话元数据（如果是新会话）
            initializeConversationMetadata(context);

            return context;
        } catch (AgentException e) {
            throw e; // 重新抛出AgentException
        } catch (Exception e) {
            throw new AgentException(
                ErrorCode.CONTEXT_BUILD_FAILED,
                "Failed to build runner context for user: " + (request != null ? request.getUserId() : "null"), e);
        }
    }

    private String buildSystemPrompt() {
        return "一个智能助手，可以帮助用户解决各种问题，包括数学计算和天气查询";
    }

    /**
     * 初始化会话元数据
     */
    private void initializeConversationMetadata(RunnerContext context) {
        if (conversationService != null) {
            conversationService.conversationExists(context.getConversationId())
                .flatMap(exists -> {
                    if (!exists) {
                        // 创建新会话的元数据
                        ConversationMetadata metadata =
                            ConversationMetadata.builder()
                                .conversationId(context.getConversationId())
                                .userId(context.getUserId())
                                .agentId(agent.getId())
                                .title("New Conversation")
                                .status(ConversationMetadata.ConversationStatus.ACTIVE)
                                .priority(ConversationMetadata.ConversationPriority.NORMAL)
                                .createdAt(java.time.LocalDateTime.now())
                                .lastActiveAt(java.time.LocalDateTime.now())
                                .build();

                        return conversationService.setConversationMetadata(context.getConversationId(), metadata);
                    }
                    return Mono.empty();
                })
                .doOnError(error -> log.warn("Failed to initialize conversation metadata", error))
                .subscribe();
        }
    }

    /**
     * 执行流式循环 - 使用智能缓冲
     */
    private Flux<AgentResponse> executeStreamLoop(RunnerContext context) {
        return Flux.defer(() -> {
            if (context.isMaxRoundsReached()) {
                return Flux.just(AgentResponse.error("Maximum rounds reached"));
            }

            return executeStreamRoundWithBuffer(context);
        });
    }

    /**
     * 执行单轮流式调用，使用智能缓冲
     */
    private Flux<AgentResponse> executeStreamRoundWithBuffer(RunnerContext context) {
        // 从配置中获取是否流式输出工具调用内容的设置
        boolean streamToolCallContent = agentConfig != null && agentConfig.getStreamToolCallContent() != null 
            ? agentConfig.getStreamToolCallContent() : true;
        StreamBuffer buffer = new StreamBuffer(streamToolCallContent);
        return agent.runStream(context)
                .flatMap(response -> {
                    StreamBuffer.BufferDecision decision = buffer.addResponse(response);

                    switch (decision) {
                        case CONTINUE_BUFFERING:
                            // 继续缓冲，不输出
                            return Flux.empty();

                        case START_STREAMING:
                            // 开始流式输出：先输出缓冲区内容，然后输出当前响应
                            List<AgentResponse> bufferedResponses = buffer.getBufferedResponses();
                            buffer.clearBuffer();
                            return Flux.fromIterable(bufferedResponses);

                        case DIRECT_OUTPUT:
                            // 直接输出当前响应
                            if (streamToolCallContent && response.getType() == AgentResponse.ResponseType.TOOL_CALL) {
                                // streamToolCallContent=true时，工具调用也直接输出，但需要在后台处理
                                // 先输出工具调用响应，然后异步处理工具调用并继续下一轮
                                return Flux.just(response)
                                    .concatWith(
                                        handleToolCallsWithDirectOutputCheck(response.getToolCalls(), context)
                                            .flatMapMany(shouldContinue -> {
                                                if (shouldContinue) {
                                                    // 继续下一轮循环
                                                    return executeStreamLoop(context);
                                                } else {
                                                    return Flux.empty();
                                                }
                                            })
                                    );
                            }
                            return Flux.just(response);

                        case WAIT_FOR_COMPLETION:
                            // 检测到工具调用，继续缓冲，不输出
                            return Flux.empty();

                        case RELEASE_ALL:
                            // 流式完成，根据配置和工具调用情况处理
                            return handleStreamCompletion(buffer, streamToolCallContent, context);

                        default:
                            return Flux.empty();
                    }
                })
                .concatWith(Flux.defer(() -> {
                    // 处理流式结束后的情况
                    if (!streamToolCallContent && buffer.isToolCallDetected() && !buffer.isStreamCompleted()) {
                        return handleStreamCompletion(buffer, streamToolCallContent, context);
                    }
                    return Flux.empty();
                }));
    }
    /**
     * 处理流式完成的情况
     */
    private Flux<AgentResponse> handleStreamCompletion(StreamBuffer buffer, boolean streamToolCallContent, RunnerContext context) {
        if (buffer.isToolCallDetected()) {
            if (streamToolCallContent) {
                // 配置为流式输出：内容已经输出，只处理工具调用
                return handleToolCallsInStream(buffer.getBufferedResponses(), context);
            } else {
                // 配置为不流式输出：忽略文本内容，只处理工具调用
                log.debug("Tool call detected with streamToolCallContent=false, ignoring text content");
                return handleToolCallsInStream(buffer.getBufferedResponses(), context);
            }
        } else {
            // 没有工具调用，输出所有内容
            List<AgentResponse> responses = streamToolCallContent ? 
                buffer.getBufferedResponses() : buffer.getTextResponses();
            return Flux.fromIterable(responses);
        }
    }

    /**
     * 处理流式响应中的工具调用
     */
    private Flux<AgentResponse> handleToolCallsInStream(List<AgentResponse> responses, RunnerContext context) {
        // 找到工具调用响应
        AgentResponse toolCallResponse = responses.stream()
                .filter(r -> r.getType() == AgentResponse.ResponseType.TOOL_CALL)
                .findFirst()
                .orElse(null);

        if (toolCallResponse != null) {
            // 执行工具调用并检查是否需要直接输出
            return handleToolCallsWithDirectOutputCheck(toolCallResponse.getToolCalls(), context)
                    .flatMapMany(shouldContinue -> {
                        if (shouldContinue) {
                            // 继续下一轮循环
                            return executeStreamLoop(context);
                        } else {
                            // 直接输出，终止循环
                            log.debug("Direct output detected, terminating stream loop");
                            return Flux.empty();
                        }
                    });
        } else {
            // 没有工具调用，直接返回响应
            return Flux.fromIterable(responses);
        }
    }

    /**
     * 执行非流式循环
     */
    private Mono<AgentResponse> executeNonStreamLoop(RunnerContext context) {
        return executeNonStreamRound(context)
                .expand(response -> {
                    if (response.getType() == AgentResponse.ResponseType.TOOL_CALL &&
                        !context.isMaxRoundsReached()) {
                        // 处理工具调用并检查是否需要继续
                        return handleToolCallsWithDirectOutputCheck(response.getToolCalls(), context)
                                .flatMap(shouldContinue -> {
                                    if (shouldContinue) {
                                        // 继续下一轮
                                        return executeNonStreamRound(context);
                                    } else {
                                        // 直接输出，终止循环，返回一个特殊的终止响应
                                        log.debug("Direct output detected, terminating non-stream loop");
                                        return Mono.just(AgentResponse.text("Direct output terminated", true));
                                    }
                                });
                    }
                    // 结束循环
                    return Mono.empty();
                })
                .filter(response -> response.getType() != AgentResponse.ResponseType.TOOL_CALL)
                .last(); // 获取最后一个非工具调用响应
    }

    /**
     * 执行单轮非流式调用
     */
    private Mono<AgentResponse> executeNonStreamRound(RunnerContext context) {
        return agent.run(context);
    }

    /**
     * 处理工具调用并检查是否需要直接输出
     * @return Mono<Boolean> - true表示继续循环，false表示终止循环
     */
    private Mono<Boolean> handleToolCallsWithDirectOutputCheck(List<ToolCall> toolCalls, RunnerContext context) {
        if (agent.getToolRegistry() == null) {
            log.error("Tool registry not available");
            return Mono.error(new AgentException(
                ErrorCode.SYSTEM_ERROR,
                "Tool registry not available for agent: " + agent.getName()));
        }

        // 检查轮次限制
        if (context.isMaxRoundsReached()) {
            return Mono.error(new AgentException(
                ErrorCode.CONTEXT_MAX_ROUNDS_EXCEEDED,
                "Maximum rounds exceeded: " + context.getCurrentRound() + "/" + context.getMaxRounds()));
        }

        // 创建ToolContext
        ToolContext toolContext = ToolContext.builder()
                .runnerContext(context)
                .currentRound(context.getCurrentRound())
                .build();

        // 执行所有工具调用
        List<Mono<ToolResult>> toolExecutions = toolCalls.stream()
                .map(toolCall -> agent.getToolRegistry().execute(toolCall, toolContext)
                    .onErrorMap(error -> new AgentException(
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "Tool execution failed: " + toolCall.getFunction().getName(), error)))
                .collect(Collectors.toList());

        return Flux.fromIterable(toolExecutions)
                .flatMap(mono -> mono)
                .collectList()
                .flatMap(results -> {
                    // 检查是否有直接输出的Agent调用
                    for (ToolResult result : results) {
                        if (isAgentDirectOutput(result)) {
                            // 处理Agent直接输出
                            return handleAgentDirectOutput(result, context)
                                    .then(Mono.just(false)); // 返回false表示终止循环
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
                    } else {
                        log.warn("All tool calls failed, not incrementing round. Current round: {}", context.getCurrentRound());
                    }

                    return Mono.just(true); // 返回true表示继续循环
                });
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

            // 设置特殊标记，让AgentRunner知道要停止Think循环
            context.addMessage(new Message(
                "system", "DIRECT_OUTPUT_MARKER"));

            return Mono.empty();

        } catch (Exception e) {
            log.error("Failed to handle agent direct output", e);
            return Mono.empty();
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

        // 根据异常类型映射到具体的错误码
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

        // 默认映射为系统错误
        return new AgentException(
            ErrorCode.SYSTEM_ERROR,
            "Unexpected error in AgentRunner", throwable);
    }

    /**
     * 创建错误事件
     */
    private AgentEvent createErrorEvent(Throwable throwable) {
        if (throwable instanceof AgentException) {
            AgentException agentException =
                (AgentException) throwable;

            log.error("Agent execution failed: [{}] {}",
                agentException.getErrorCode().getCode(),
                agentException.getMessage(),
                agentException);

            // 根据错误级别决定是否暴露详细信息给用户
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
