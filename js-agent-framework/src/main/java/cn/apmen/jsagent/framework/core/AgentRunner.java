package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.conversation.ConversationMetadata;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.core.ContextInformation.ConversationInformation;
import cn.apmen.jsagent.framework.core.ContextInformation.EnvironmentInformation;
import cn.apmen.jsagent.framework.core.ContextInformation.UserInformation;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.execution.ExecutionContext;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import cn.apmen.jsagent.framework.stream.StreamBuffer;
import cn.apmen.jsagent.framework.tool.AgentTool;
import cn.apmen.jsagent.framework.tool.AgentToolResponse;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final ConversationService conversationService;
    private final Mustache.Compiler mustacheCompiler = Mustache.compiler();

    // 默认系统提示词模板
    private static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        你是一个智能助手，可以帮助用户解决各种问题，包括数学计算和天气查询。
        {{#userInfo}}
        用户信息：
        - 用户名：{{username}}
        - 首选语言：{{preferredLanguage}}
        - 时区：{{timezone}}
        {{#preferences.responseStyle}}
        - 请使用{{.}}的回复风格
        {{/preferences.responseStyle}}
        {{/userInfo}}
        {{#conversationInfo}}
        {{^isNewConversation}}
        这是一个继续的对话，请保持上下文的连贯性。
        {{/isNewConversation}}
        {{/conversationInfo}}
        {{#environmentInfo}}
        当前时间：{{currentTime}}
        {{#availableTools}}
        可用工具：{{#.}}{{.}}{{^-last}}, {{/-last}}{{/.}}
        {{/availableTools}}
        {{/environmentInfo}}
        请根据用户的需求提供准确、有用的回答。
        """;

    // 配置ObjectMapper
    {
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

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
        return loadContextInformation(request)
                .flatMapMany(contextInfo -> {
                    // 发送开始执行的调试事件
                    return Flux.just(AgentEvent.debugInfo("开始执行Agent任务，用户ID: " + contextInfo.getUserId() +
                                                        ", 会话ID: " + contextInfo.getConversationId()))
                            .concatWith(buildRunnerContextWithInfo(request, contextInfo)
                                    .flatMapMany(context -> {
                                        // 先添加用户消息到ConversationService
                                        return addUserMessageToConversation(request, context)
                                                .thenMany(executeStreamLoop(context))
                                                .flatMap(this::convertToAgentEventStream);
                                    }));
                })
                .doOnNext(event -> log.debug("Agent event generated: type={}, content={}",
                    event.getType(),
                    event.getContent() != null ? event.getContent().substring(0, Math.min(50, event.getContent().length())) + "..." : "null"))
                .onErrorMap(this::mapToAgentException)
                .onErrorResume(throwable -> Flux.just(createErrorEvent(throwable)));
    }

    /**
     * 非流式运行Agent
     * @param request 用户聊天请求
     * @return Agent事件
     */
    public Mono<AgentEvent> run(UserChatRequest request) {
        return loadContextInformation(request)
                .flatMap(contextInfo -> buildRunnerContextWithInfo(request, contextInfo))
                .flatMap(context -> addUserMessageToConversation(request, context)
                        .then(executeNonStreamLoop(context)))
                .map(this::convertToAgentEvent)
                .doOnNext(event -> log.debug("Agent event generated: {}", event))
                .onErrorMap(this::mapToAgentException)
                .onErrorResume(throwable -> Mono.just(createErrorEvent(throwable)));
    }

    /**
     * 加载上下文信息（用户信息、会话信息、环境信息）
     */
    private Mono<ContextInformation> loadContextInformation(UserChatRequest request) {
        return Mono.fromCallable(() -> {
            // 验证请求参数
            validateRequest(request);

            log.debug("Loading context information for user: {}, conversation: {}",
                request.getUserId(), request.getConversationId());

            return ContextInformation.builder()
                    .userId(request.getUserId())
                    .conversationId(request.getConversationId())
                    .loadTime(LocalDateTime.now())
                    .build();
        })
        .flatMap(this::enrichContextInformation);
    }

    /**
     * 丰富上下文信息
     */
    private Mono<ContextInformation> enrichContextInformation(ContextInformation contextInfo) {
        return Mono.zip(
                loadUserInformation(contextInfo.getUserId()),
                loadConversationInformation(contextInfo.getConversationId()),
                loadEnvironmentInformation()
        ).map(tuple -> {
            UserInformation userInfo = tuple.getT1();
            ConversationInformation conversationInfo = tuple.getT2();
            EnvironmentInformation envInfo = tuple.getT3();

            return contextInfo.toBuilder()
                    .userInformation(userInfo)
                    .conversationInformation(conversationInfo)
                    .environmentInformation(envInfo)
                    .build();
        });
    }

    /**
     * 加载用户信息（可以从用户服务、数据库等获取）
     */
    private Mono<UserInformation> loadUserInformation(String userId) {
        return Mono.fromCallable(() -> {
            // TODO: 实际实现中应该从用户服务或数据库加载
            // 这里先mock一些基本信息
            log.debug("Loading user information for user: {}", userId);

            return UserInformation.builder()
                    .userId(userId)
                    .username("User_" + userId)
                    .preferredLanguage("zh-CN")
                    .timezone("Asia/Shanghai")
                    .userLevel("standard")
                    .preferences(java.util.Map.of(
                        "responseStyle", "friendly",
                        "detailLevel", "medium"
                    ))
                    .build();
        });
    }

    /**
     * 加载会话信息
     */
    private Mono<ConversationInformation> loadConversationInformation(String conversationId) {
        if (conversationService == null) {
            return Mono.just(ConversationInformation.builder()
                    .conversationId(conversationId)
                    .isNewConversation(true)
                    .build());
        }

        return conversationService.conversationExists(conversationId)
                .flatMap(exists -> {
                    if (exists) {
                        return conversationService.getConversationMetadata(conversationId)
                                .map(metadata -> ConversationInformation.builder()
                                        .conversationId(conversationId)
                                        .isNewConversation(false)
                                        .metadata(metadata)
                                        .messageCount(0) // TODO: 从统计信息获取
                                        .lastActiveTime(metadata.getLastActiveAt())
                                        .build())
                                .switchIfEmpty(Mono.just(ConversationInformation.builder()
                                        .conversationId(conversationId)
                                        .isNewConversation(false)
                                        .build()));
                    } else {
                        return Mono.just(ConversationInformation.builder()
                                .conversationId(conversationId)
                                .isNewConversation(true)
                                .build());
                    }
                });
    }

    /**
     * 加载环境信息
     */
    private Mono<EnvironmentInformation> loadEnvironmentInformation() {
        return Mono.fromCallable(() -> {
            // TODO: 实际实现中可以加载系统状态、配置信息等
            log.debug("Loading environment information");

            return EnvironmentInformation.builder()
                    .currentTime(LocalDateTime.now())
                    .systemVersion("1.0.0")
                    .availableTools(agent.getToolRegistry() != null ?
                        agent.getToolRegistry().getRegisteredToolNames() : List.of())
                    .systemLoad("normal")
                    .build();
        });
    }

    /**
     * 使用上下文信息构建RunnerContext
     */
    private Mono<RunnerContext> buildRunnerContextWithInfo(UserChatRequest request, ContextInformation contextInfo) {
        return Mono.fromCallable(() -> {
            try {
                // 生成执行ID
                String executionId = UUID.randomUUID().toString();

                // 构建执行上下文
                ExecutionContext executionContext = ExecutionContext.builder()
                        .executionId(executionId)
                        .agentId(agent.getId())
                        .userId(request.getUserId())
                        .conversationId(request.getConversationId())
                        .build();

                // 构建系统提示词
                String systemPrompt = buildSystemPromptWithContext(contextInfo);

                RunnerContext.RunnerContextBuilder builder = RunnerContext.builder()
                        .conversationService(conversationService)
                        .userId(request.getUserId())
                        .conversationId(request.getConversationId())
                        .executionContext(executionContext)
                        .systemPrompt(systemPrompt);

                // 设置上下文token限制
                if (agentConfig != null && agentConfig.getMaxContextTokens() != null) {
                    builder.maxContextTokens(agentConfig.getMaxContextTokens());
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
                if (contextInfo.getConversationInformation().isNewConversation()) {
                    initializeConversationMetadata(context, contextInfo);
                }

                log.debug("Built RunnerContext with execution ID: {}", executionId);
                return context;

            } catch (AgentException e) {
                throw e;
            } catch (Exception e) {
                throw new AgentException(
                    ErrorCode.CONTEXT_BUILD_FAILED,
                    "Failed to build runner context for user: " + request.getUserId(), e);
            }
        });
    }

    /**
     * 根据上下文信息构建系统提示词，支持Mustache模板渲染
     */
    private String buildSystemPromptWithContext(ContextInformation contextInfo) {
        try {
            // 获取系统提示词模板
            String template = getSystemPromptTemplate();
            // 构建模板数据
            Map<String, Object> templateData = buildTemplateData(contextInfo);
            // 使用Mustache渲染模板
            Template compiledTemplate = mustacheCompiler.compile(template);
            String renderedPrompt = compiledTemplate.execute(templateData).trim();
            log.debug("Rendered system prompt: {}", renderedPrompt.substring(0, Math.min(100, renderedPrompt.length())) + "...");
            return renderedPrompt;
        } catch (Exception e) {
            log.warn("Failed to render system prompt template, using fallback", e);
            return buildFallbackSystemPrompt(contextInfo);
        }
    }

    /**
     * 获取系统提示词模板
     */
    private String getSystemPromptTemplate() {
        // 可以从配置文件、数据库等获取自定义模板
        if (agentConfig != null && agentConfig.getSystemPromptTemplate() != null) {
            return agentConfig.getSystemPromptTemplate();
        }
        return DEFAULT_SYSTEM_PROMPT_TEMPLATE;
    }

    /**
     * 构建模板数据
     */
    private Map<String, Object> buildTemplateData(ContextInformation contextInfo) {
        Map<String, Object> data = new HashMap<>();
        // 用户信息
        if (contextInfo.getUserInformation() != null) {
            data.put("userInfo", contextInfo.getUserInformation());
        }
        // 会话信息
        if (contextInfo.getConversationInformation() != null) {
            data.put("conversationInfo", contextInfo.getConversationInformation());
        }
        // 环境信息
        if (contextInfo.getEnvironmentInformation() != null) {
            data.put("environmentInfo", contextInfo.getEnvironmentInformation());
        }
        return data;
    }

    /**
     * 构建降级系统提示词（当模板渲染失败时使用）
     */
    private String buildFallbackSystemPrompt(ContextInformation contextInfo) {
        StringBuilder promptBuilder = new StringBuilder();

        // 基础系统提示
        promptBuilder.append("你是一个智能助手，可以帮助用户解决各种问题，包括数学计算和天气查询。\n\n");

        // 用户信息相关的提示
        UserInformation userInfo = contextInfo.getUserInformation();
        if (userInfo != null) {
            promptBuilder.append("用户信息：\n");
            promptBuilder.append("- 用户名：").append(userInfo.getUsername()).append("\n");
            promptBuilder.append("- 首选语言：").append(userInfo.getPreferredLanguage()).append("\n");
            promptBuilder.append("- 时区：").append(userInfo.getTimezone()).append("\n");

            if (userInfo.getPreferences() != null) {
                String responseStyle = (String) userInfo.getPreferences().get("responseStyle");
                if (responseStyle != null) {
                    promptBuilder.append("- 请使用").append(responseStyle).append("的回复风格\n");
                }
            }
            promptBuilder.append("\n");
        }

        // 会话信息相关的提示
        ConversationInformation convInfo = contextInfo.getConversationInformation();
        if (convInfo != null && !convInfo.isNewConversation()) {
            promptBuilder.append("这是一个继续的对话，请保持上下文的连贯性。\n\n");
        }

        // 环境信息相关的提示
        EnvironmentInformation envInfo = contextInfo.getEnvironmentInformation();
        if (envInfo != null) {
            promptBuilder.append("当前时间：").append(envInfo.getCurrentTime()).append("\n");
            if (envInfo.getAvailableTools() != null && !envInfo.getAvailableTools().isEmpty()) {
                promptBuilder.append("可用工具：").append(String.join(", ", envInfo.getAvailableTools())).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("请根据用户的需求提供准确、有用的回答。");

        return promptBuilder.toString();
    }

    /**
     * 添加用户消息到ConversationService
     */
    private Mono<Void> addUserMessageToConversation(UserChatRequest request, RunnerContext context) {
        if (request.getMessage() == null || request.getMessage().getMessage() == null) {
            return Mono.empty();
        }

        Message userMessage = new Message("user", request.getMessage().getMessage());

        // 先添加到ConversationService进行持久化
        if (conversationService != null) {
            return conversationService.addMessage(context.getConversationId(), userMessage)
                    .doOnSuccess(v -> {
                        // 成功持久化后，添加到RunnerContext的本地缓存
                        context.addMessage(userMessage);
                        log.debug("User message added to conversation: {}", request.getMessage().getMessage());
                    })
                    .doOnError(error -> {
                        log.warn("Failed to persist user message, adding to local cache only", error);
                        // 即使持久化失败，也要添加到本地缓存以保证功能正常
                        context.addMessage(userMessage);
                    });
        } else {
            // 没有ConversationService时，直接添加到本地缓存
            context.addMessage(userMessage);
            return Mono.empty();
        }
    }

    /**
     * 验证请求参数
     */
    private void validateRequest(UserChatRequest request) {
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
    }

    /**
     * 初始化会话元数据
     */
    private void initializeConversationMetadata(RunnerContext context, ContextInformation contextInfo) {
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
                                .createdAt(LocalDateTime.now())
                                .lastActiveAt(LocalDateTime.now())
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

            // 发送进入思考循环的调试事件
            return Flux.just(AgentResponse.debug("进入思考循环，当前轮次: " + context.getCurrentRound()))
                    .concatWith(executeStreamRoundWithBuffer(context));
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

                        case DIRECT_OUTPUT:
                            // 直接输出当前响应（streamToolCallContent=true模式）
                            return Flux.just(response);

                        case RELEASE_ALL:
                            // 流式完成，根据配置和工具调用情况处理
                            return handleStreamCompletion(buffer, streamToolCallContent, context);

                        default:
                            return Flux.empty();
                    }
                });
    }
    /**
     * 处理流式完成的情况
     */
    private Flux<AgentResponse> handleStreamCompletion(StreamBuffer buffer, boolean streamToolCallContent, RunnerContext context) {
        if (buffer.isToolCallDetected()) {
            // 检测到工具调用，需要执行工具
            return handleToolCallsInStream(buffer.getBufferedResponses(), context);
        } else {
            // 没有工具调用
            if (streamToolCallContent) {
                // streamToolCallContent=true: 内容已经通过DIRECT_OUTPUT输出，无需再输出
                return Flux.empty();
            } else {
                // streamToolCallContent=false: 内容被缓冲，现在输出文本内容
                return Flux.fromIterable(buffer.getTextResponses());
            }
        }
    }

    /**
     * 处理流式响应中的工具调用
     */
    private Flux<AgentResponse> handleToolCallsInStream(List<AgentResponse> responses, RunnerContext context) {
        // 找到工具调用响应（调用此方法时已确保存在工具调用）
        AgentResponse toolCallResponse = responses.stream()
                .filter(r -> r.getType() == AgentResponse.ResponseType.TOOL_CALL)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Expected tool call response but not found"));

        // 执行工具调用
        return handleToolCallsWithDirectOutputCheck(toolCallResponse.getToolCalls(), context);
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
                                .filter(r -> r.getType() == AgentResponse.ResponseType.TEXT)
                                .next()
                                .switchIfEmpty(Mono.just(AgentResponse.text("Direct output terminated", true)));
                    }
                    // 结束循环
                    return Mono.empty();
                })
                .filter(response -> response.getType() == AgentResponse.ResponseType.TEXT)
                .last(); // 获取最后一个文本响应
    }

    /**
     * 执行单轮非流式调用
     */
    private Mono<AgentResponse> executeNonStreamRound(RunnerContext context) {
        return agent.run(context);
    }

    /**
     * 处理工具调用并检查是否需要直接输出
     * @return Flux<AgentResponse> - 包含工具结果事件和后续响应
     */
    private Flux<AgentResponse> handleToolCallsWithDirectOutputCheck(List<ToolCall> toolCalls, RunnerContext context) {
        if (agent.getToolRegistry() == null) {
            log.error("Tool registry not available");
            return Flux.just(AgentResponse.error("Tool registry not available for agent: " + agent.getName()));
        }

        // 检查轮次限制
        if (context.isMaxRoundsReached()) {
            return Flux.just(AgentResponse.error("Maximum rounds exceeded: " + context.getCurrentRound() + "/" + context.getMaxRounds()));
        }

        // 创建ToolContext
        ToolContext toolContext = ToolContext.builder()
                .runnerContext(context)
                .currentRound(context.getCurrentRound())
                .build();

        // 检查是否有AgentTool设置为directOutput=true
        ToolCall directOutputAgentToolCall = toolCalls.stream()
                .filter(this::isDirectOutputAgentTool)
                .findFirst()
                .orElse(null);

        if (directOutputAgentToolCall != null) {
            log.debug("Detected AgentTool with directOutput=true, will execute only this tool and terminate loop");
            // 只执行这个AgentTool，忽略其他工具调用
            return agent.getToolRegistry().execute(directOutputAgentToolCall, toolContext)
                    .onErrorMap(error -> new AgentException(
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "Tool execution failed: " + directOutputAgentToolCall.getFunction().getName(), error))
                    .flatMapMany(result -> {
                        // 处理工具调用结果
                        String content = result.isSuccess() ? result.getContent() : result.getError();
                        context.addToolMessage(result.getToolCallId(), content);

                        // 检查是否有直接输出的Agent调用
                        if (isAgentDirectOutput(result)) {
                            // 处理Agent直接输出 - 直接将内容添加到上下文，不需要特殊标记
                            return extractDirectOutputContent(result, context)
                                    .flatMapMany(agentContent -> {
                                        if (agentContent != null && !agentContent.trim().isEmpty()) {
                                            // 将内容作为助手消息添加到上下文
                                            context.addMessage(new Message("assistant", agentContent));
                                            log.debug("DirectOutput AgentTool content added to context: {}", agentContent);
                                            // 直接返回TEXT_RESPONSE事件
                                            return Flux.just(AgentResponse.text(agentContent, true));
                                        }
                                        return Flux.empty();
                                    });
                        }
                        log.debug("AgentTool with directOutput=true executed, terminating loop");
                        return Flux.empty();
                    });
        }

        // 没有directOutput=true的AgentTool，执行所有工具调用
        List<Mono<ToolResult>> toolExecutions = toolCalls.stream()
                .map(toolCall -> agent.getToolRegistry().execute(toolCall, toolContext)
                    .onErrorMap(error -> new AgentException(
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        "Tool execution failed: " + toolCall.getFunction().getName(), error)))
                .collect(Collectors.toList());

        return Flux.fromIterable(toolExecutions)
                .flatMap(mono -> mono)
                .collectList()
                .flatMapMany(results -> {
                    // 检查是否有直接输出的Agent调用
                    for (ToolResult result : results) {
                        if (isAgentDirectOutput(result)) {
                            // 处理Agent直接输出
                            return handleAgentDirectOutput(result, context)
                                    .thenMany(Flux.empty()); // 返回空流表示终止循环
                        }
                    }

                    // 普通工具调用处理 - 输出TOOL_RESULT事件
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

                    // 创建TOOL_RESULT事件并继续下一轮循环
                    AgentResponse toolResultResponse = AgentResponse.builder()
                            .type(AgentResponse.ResponseType.TOOL_CALL) // 临时使用TOOL_CALL类型，后续会转换为TOOL_RESULT事件
                            .content("Tool execution completed")
                            .isFinalResponse(false)
                            .build();

                    return Flux.just(toolResultResponse)
                            .concatWith(executeStreamLoop(context));
                });
    }

    /**
     * 检查工具调用是否为设置了directOutput=true的AgentTool
     */
    private boolean isDirectOutputAgentTool(ToolCall toolCall) {
        if (agent.getToolRegistry() == null) {
            return false;
        }
        String toolName = toolCall.getFunction().getName();
        ToolExecutor executor = agent.getToolRegistry().getExecutor(toolName);
        // 检查是否为AgentTool且设置了directOutput=true
        if (executor instanceof AgentTool) {
            AgentTool agentTool =
                (AgentTool) executor;
            return agentTool.isDirectOutput();
        }
        return false;
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

            log.info("Agent direct output detected: Agent={}, DirectOutput={}",
                agentResponse.getTargetAgentName() != null ? agentResponse.getTargetAgentName() : agentResponse.getTargetAgentId(),
                agentResponse.isDirectOutput());

            // 标记上下文为直接输出模式
            String agentName = agentResponse.getTargetAgentName() != null ?
                agentResponse.getTargetAgentName() : agentResponse.getTargetAgentId();
            context.addSystemMessage("Agent " + agentName +
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
     * 提取并添加直接输出内容到上下文
     */
    private Mono<Void> extractAndAddDirectOutputContent(ToolResult result, RunnerContext context) {
        try {
            // 解析AgentToolResponse
            String jsonContent = result.getContent().substring("AGENT_DIRECT_OUTPUT:".length());
            AgentToolResponse agentResponse = objectMapper.readValue(jsonContent, AgentToolResponse.class);

            // 将AgentTool的内容作为助手消息添加到上下文中
            if (agentResponse.getContent() != null && !agentResponse.getContent().trim().isEmpty()) {
                context.addMessage(new Message("assistant", agentResponse.getContent()));
                log.debug("Added direct output content to context: {}", agentResponse.getContent());
            }

            return Mono.empty();

        } catch (Exception e) {
            log.error("Failed to extract direct output content", e);
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
     * 将AgentResponse转换为AgentEvent
     */
    private Flux<AgentEvent> convertToAgentEventStream(AgentResponse response) {
        switch (response.getType()) {
            case TEXT:
                return Flux.just(AgentEvent.textResponse(response.getContent(), response.isFinalResponse()));
            case TOOL_CALL:
                return Flux.just(AgentEvent.toolCall(response.getToolCalls()));
            case ERROR:
                return Flux.just(AgentEvent.error(response.getError()));
            case THINKING:
                return Flux.just(AgentEvent.thinking(response.getContent()));
            case DEBUG:
                return Flux.just(AgentEvent.debug(response.getContent()));
            default:
                return Flux.just(AgentEvent.error("Unknown response type"));
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

    private Flux<AgentResponse> outputDirectOutputContentFromContext(RunnerContext context) {
        // 查找上下文中的直接输出内容标记
        List<Message> messages = context.getMessageHistory();
        for (Message message : messages) {
            if ("system".equals(message.getRole()) &&
                message.getContent() != null &&
                message.getContent().startsWith("DIRECT_OUTPUT_CONTENT:")) {

                String content = message.getContent().substring("DIRECT_OUTPUT_CONTENT:".length());
                log.debug("Found direct output content in context: {}", content);

                // 创建TEXT_RESPONSE事件
                AgentResponse textResponse = AgentResponse.text(content, true);
                return Flux.just(textResponse);
            }
        }

        log.debug("No direct output content found in context");
        return Flux.empty();
    }

    /**
     * 提取直接输出内容
     */
    private Mono<String> extractDirectOutputContent(ToolResult result, RunnerContext context) {
        try {
            // 解析AgentToolResponse
            String jsonContent = result.getContent().substring("AGENT_DIRECT_OUTPUT:".length());
            AgentToolResponse agentResponse = objectMapper.readValue(jsonContent, AgentToolResponse.class);

            // 返回AgentTool的内容
            String content = agentResponse.getContent();
            log.debug("Extracted direct output content: {}", content);

            // 将内容作为助手消息添加到上下文中
            if (content != null && !content.trim().isEmpty()) {
                context.addMessage(new Message("assistant", content));
                log.debug("Added direct output content to context as assistant message: {}", content);
            }

            return Mono.just(content != null ? content : "");

        } catch (Exception e) {
            log.error("Failed to extract direct output content", e);
            return Mono.just("");
        }
    }

    private Flux<AgentResponse> createTextResponseFromLatestAssistantMessage(RunnerContext context) {
        // 查找上下文中的最新助手消息
        List<Message> messages = context.getMessageHistory();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if ("assistant".equals(message.getRole())) {
                // 创建TEXT_RESPONSE事件
                AgentResponse textResponse = AgentResponse.text(message.getContent(), true);
                return Flux.just(textResponse);
            }
        }

        log.debug("No assistant message found in context");
        return Flux.empty();
    }

    private Flux<AgentResponse> handleDirectOutputAgentToolStreaming(List<ToolCall> toolCalls, RunnerContext context) {
        // 创建ToolContext
        ToolContext toolContext = ToolContext.builder()
                .runnerContext(context)
                .currentRound(context.getCurrentRound())
                .build();

        // 检查是否有AgentTool设置为directOutput=true
        ToolCall directOutputAgentToolCall = toolCalls.stream()
                .filter(this::isDirectOutputAgentTool)
                .findFirst()
                .orElse(null);

        if (directOutputAgentToolCall != null) {
            log.debug("Detected AgentTool with directOutput=true, will execute stream and output");
            // 获取AgentTool执行器
            String toolName = directOutputAgentToolCall.getFunction().getName();
            ToolExecutor executor = agent.getToolRegistry().getExecutor(toolName);
            if (executor instanceof AgentTool) {
                AgentTool agentTool = (AgentTool) executor;
                log.debug("Calling AgentTool.executeStream for directOutput streaming");
                // 调用AgentTool的executeStream方法
                return agentTool.executeStream(directOutputAgentToolCall, toolContext)
                        .cast(AgentToolResponse.class)
                        .filter(response -> response.getContent() != null && !response.getContent().trim().isEmpty())
                        .map(response -> {
                            // 将AgentTool的流式内容转换为TEXT_RESPONSE
                            log.debug("Converting AgentTool stream content to TEXT_RESPONSE: {}", response.getContent());
                            return AgentResponse.text(response.getContent(), response.isDecisionFragment());
                        })
                        .doOnNext(response -> {
                            // 将内容添加到上下文
                            if (response.getContent() != null && !response.getContent().trim().isEmpty()) {
                                context.addMessage(new Message("assistant", response.getContent()));
                                log.debug("Added AgentTool stream content to context: {}", response.getContent());
                            }
                        })
                        .onErrorMap(error -> new AgentException(
                            ErrorCode.TOOL_EXECUTION_FAILED,
                            "AgentTool stream execution failed: " + toolName, error))
                        .onErrorReturn(AgentResponse.error("AgentTool stream execution failed"));
            }
        }

        return Flux.empty();
    }
}
