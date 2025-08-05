package cn.apmen.jsagent.framework.agent;

import cn.apmen.jsagent.framework.core.AgentRequest;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.openaiunified.model.request.ChatCompletionRequest;
import cn.apmen.jsagent.framework.openaiunified.model.request.Function;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.openaiunified.model.response.ChatCompletionResponse;
import cn.apmen.jsagent.framework.openaiunified.model.response.Choice;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import cn.apmen.jsagent.framework.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 完整的工具调用Agent实现
 * 支持ReAct模式：Reasoning + Acting
 */
@Slf4j
public class ToolCallAgent extends ReactAgent {

    // 核心组件
    private final OpenAIUnifiedChatClient llmClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 配置参数
    private final String model = "anthropic.claude-sonnet-4";
    private final Double temperature = 0.7;
    private final Integer maxTokens = 2000;
    private final Integer maxContextTokens = 4000;

    // 工具列表（从ToolRegistry动态获取）
    private List<Tool> availableTools;

    /**
     * 构造函数
     */
    public ToolCallAgent(String id, String name, String description, String systemPrompt,
                        Integer maxSteps, OpenAIUnifiedChatClient llmClient, ToolRegistry toolRegistry) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.maxSteps = maxSteps != null ? maxSteps : 10;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;

        // 初始化可用工具列表
        this.availableTools = buildAvailableTools();

        log.info("ToolCallAgent initialized: {} with {} tools", name, availableTools.size());
    }

    /**
     * Think阶段：调用LLM进行推理，可能产生工具调用
     */
    @Override
    public Flux<AgentResponse> think(AgentRequest agentRequest) {
        return Mono.fromCallable(() -> {
            log.debug("ToolCallAgent {} starting think phase", name);
            return buildChatCompletionRequest(agentRequest);
        })
        .flatMap(request -> {
            log.debug("Sending request to LLM with {} tools available", availableTools.size());
            return llmClient.createChatCompletion(request);
        })
        .map(this::parseThinkResponse)
        .doOnNext(response -> {
            log.debug("Think phase completed, response type: {}", response.getType());
            // 保存LLM的响应到内存
            if (response.getType() == AgentResponse.ResponseType.TEXT && response.getContent() != null) {
                addMessageToMemory(new Message("assistant", response.getContent())).subscribe();
            }
        })
        .flux()
        .onErrorMap(this::mapToAgentException);
    }

    /**
     * Act阶段：执行工具调用
     */
    @Override
    public Mono<AgentResponse> act(AgentRequest agentRequest) {
        if (agentRequest.getToolCalls() == null || agentRequest.getToolCalls().isEmpty()) {
            return Mono.just(AgentResponse.text("No tool calls to execute", true));
        }

        log.debug("ToolCallAgent {} starting act phase with {} tool calls",
                 name, agentRequest.getToolCalls().size());

        return Flux.fromIterable(agentRequest.getToolCalls())
            .flatMap(this::executeToolCall)
            .collectList()
            .map(this::buildActResponse)
            .doOnNext(response -> {
                log.debug("Act phase completed with {} tool results", agentRequest.getToolCalls().size());
            })
            .onErrorMap(this::mapToAgentException);
    }

    /**
     * 执行单个工具调用
     */
    private Mono<ToolResult> executeToolCall(ToolCall toolCall) {
        String toolName = toolCall.getFunction().getName();
        log.debug("Executing tool: {} with ID: {}", toolName, toolCall.getId());

        // 创建工具上下文
        ToolContext toolContext = ToolContext.builder()
            .toolCallId(toolCall.getId())
            .toolName(toolName)
            .build();

        return toolRegistry.execute(toolCall, toolContext)
            .doOnNext(result -> {
                log.debug("Tool {} execution completed, success: {}", toolName, result.isSuccess());
                // 保存工具结果到内存
                String content = result.isSuccess() ? result.getContent() : result.getError();
                addMessageToMemory(new Message("tool", content, toolCall.getId())).subscribe();
            })
            .onErrorResume(error -> {
                log.error("Tool execution failed for {}: {}", toolName, error.getMessage());
                return Mono.just(ToolResult.error(toolCall.getId(), "Tool execution failed: " + error.getMessage()));
            });
    }

    /**
     * 构建聊天请求
     */
    private ChatCompletionRequest buildChatCompletionRequest(AgentRequest agentRequest) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);

        // 构建消息列表
        List<Message> messages = buildMessageList(agentRequest);
        request.setMessages(messages);

        // 设置可用工具
        if (availableTools != null && !availableTools.isEmpty()) {
            request.setTools(availableTools);
            request.setToolChoice("auto");
        }

        return request;
    }

    /**
     * 构建消息列表
     */
    private List<Message> buildMessageList(AgentRequest agentRequest) {
        List<Message> messages = new ArrayList<>();

        // 1. 添加系统提示词
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            messages.add(new Message("system", systemPrompt));
        }

        // 2. 获取历史消息（考虑上下文窗口限制）
        List<Message> historyMessages = getContextWindowMessages(maxContextTokens)
            .blockOptional()
            .orElse(new ArrayList<>());
        messages.addAll(historyMessages);

        // 3. 添加当前用户消息（如果有）
        if (agentRequest.getUserMessage() != null && !agentRequest.getUserMessage().trim().isEmpty()) {
            Message userMessage = new Message("user", agentRequest.getUserMessage());
            messages.add(userMessage);
            // 保存用户消息到内存
            addMessageToMemory(userMessage).subscribe();
        }

        log.debug("Built message list with {} messages for LLM request", messages.size());
        return messages;
    }

    /**
     * 解析Think阶段的响应
     */
    private AgentResponse parseThinkResponse(ChatCompletionResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return AgentResponse.error("No response choices available");
        }

        Choice choice = response.getChoices().get(0);
        Message message = choice.getMessage();

        if (message == null) {
            return AgentResponse.error("No message in response");
        }

        // 检查是否有工具调用
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            log.debug("LLM requested {} tool calls", message.getToolCalls().size());
            return AgentResponse.toolCall(message.getToolCalls());
        }

        // 普通文本响应
        String content = message.getContent() != null ? message.getContent() : "";
        return AgentResponse.text(content, true);
    }

    /**
     * 构建Act阶段的响应
     */
    private AgentResponse buildActResponse(List<ToolResult> toolResults) {
        if (toolResults.isEmpty()) {
            return AgentResponse.text("No tool results", false);
        }

        // 检查是否所有工具都执行成功
        boolean allSuccess = toolResults.stream().allMatch(ToolResult::isSuccess);

        if (allSuccess) {
            // 构建工具结果摘要
            String summary = toolResults.stream()
                .map(result -> String.format("Tool %s: %s",
                    result.getToolCallId(),
                    result.getContent().length() > 100 ?
                        result.getContent().substring(0, 100) + "..." :
                        result.getContent()))
                .collect(Collectors.joining("\n"));

            return AgentResponse.text("Tools executed successfully:\n" + summary, false);
        } else {
            // 有工具执行失败
            String errors = toolResults.stream()
                .filter(result -> !result.isSuccess())
                .map(result -> String.format("Tool %s failed: %s",
                    result.getToolCallId(), result.getError()))
                .collect(Collectors.joining("\n"));

            return AgentResponse.error("Some tools failed:\n" + errors);
        }
    }

    /**
     * 构建可用工具列表
     */
    private List<Tool> buildAvailableTools() {
        List<Tool> tools = new ArrayList<>();

        if (toolRegistry != null) {
            // 直接从ToolRegistry获取所有工具的Tool对象
            tools.addAll(toolRegistry.getAllTools());
            log.debug("Loaded {} tools from ToolRegistry", tools.size());
        }

        // 添加内置的终止工具
        tools.add(buildTerminateTool());

        return tools;
    }

    /**
     * 构建终止工具
     */
    private Tool buildTerminateTool() {
        TerminateTool terminateTool = new TerminateTool(this);
        return terminateTool.buildTool();
    }

    /**
     * 异常映射
     */
    private Throwable mapToAgentException(Throwable error) {
        if (error instanceof AgentException) {
            return error;
        }
        return new AgentException(ErrorCode.AGENT_EXECUTION_FAILED,
            "ToolCallAgent execution failed: " + error.getMessage(), error);
    }

    // 实现Agent接口的方法
    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public Mono<AgentResponse> call(String message) {
        AgentRequest request = AgentRequest.builder()
            .userMessage(message)
            .build();

        return run(request)
            .last()
            .onErrorMap(this::mapToAgentException);
    }

    @Override
    public Flux<AgentResponse> callStream(String message) {
        AgentRequest request = AgentRequest.builder()
            .userMessage(message)
            .build();

        return run(request)
            .onErrorMap(this::mapToAgentException);
    }

    /**
     * 添加工具到可用工具列表
     */
    public void addTool(Tool tool) {
        if (availableTools == null) {
            availableTools = new ArrayList<>();
        }
        availableTools.add(tool);
        log.debug("Added tool {} to ToolCallAgent {}", tool.getFunction().getName(), name);
    }

    /**
     * 刷新可用工具列表
     */
    public void refreshTools() {
        this.availableTools = buildAvailableTools();
        log.debug("Refreshed tools for ToolCallAgent {}, now has {} tools", name, availableTools.size());
    }

    /**
     * 获取可用工具数量
     */
    public int getAvailableToolsCount() {
        return availableTools != null ? availableTools.size() : 0;
    }

    /**
     * 使用指定的工具列表
     * @param toolNames 工具名称列表
     */
    public void useSpecificTools(List<String> toolNames) {
        if (toolRegistry != null) {
            List<Tool> specificTools = new ArrayList<>(toolRegistry.getTools(toolNames));
            specificTools.add(buildTerminateTool()); // 添加终止工具
            this.availableTools = specificTools;
            log.info("Switched to specific tools: {} ({} tools)", toolNames, specificTools.size());
        }
    }

    /**
     * 使用工具名称模式匹配
     * @param pattern 工具名称模式（支持通配符*）
     */
    public void useToolsByPattern(String pattern) {
        if (toolRegistry != null) {
            List<Tool> matchedTools = new ArrayList<>(toolRegistry.getToolsByPattern(pattern));
            matchedTools.add(buildTerminateTool()); // 添加终止工具
            this.availableTools = matchedTools;
            log.info("Switched to tools matching pattern '{}' ({} tools)", pattern, matchedTools.size());
        }
    }

    /**
     * 重置为使用所有可用工具
     */
    public void useAllTools() {
        this.availableTools = buildAvailableTools();
        log.info("Reset to use all available tools ({} tools)", availableTools.size());
    }
}
