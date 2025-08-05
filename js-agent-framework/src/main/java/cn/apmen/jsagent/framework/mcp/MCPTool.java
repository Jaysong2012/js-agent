package cn.apmen.jsagent.framework.mcp;

import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.AbstractToolExecutor;
import cn.apmen.jsagent.framework.tool.BaseToolResponse;
import cn.apmen.jsagent.framework.tool.StreamingToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP工具 - 将MCP服务包装为工具
 * 继承AbstractToolExecutor，符合新的工具设计规范
 * 同时实现StreamingToolExecutor以支持流式执行
 */
@Slf4j
public class MCPTool extends AbstractToolExecutor implements StreamingToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // MCP客户端 - 实际执行功能的客户端
    private final McpSyncClient mcpClient;
    // MCP工具名称
    private final String mcpToolName;
    // 是否直接输出给用户
    private final boolean directOutput;
    // 工具名称
    private final String toolName;
    // 工具描述
    private final String description;
    // 参数定义
    private final Map<String, Object> parametersDefinition;
    // 必需参数
    private final String[] requiredParameters;

    // 配置ObjectMapper支持Java 8时间类型
    {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 构造函数
     * @param toolName 工具名称
     * @param description 工具描述
     * @param parametersDefinition 参数定义
     * @param requiredParameters 必需参数
     * @param mcpClient MCP客户端
     * @param mcpToolName MCP工具名称
     * @param directOutput 是否直接输出给用户
     */
    public MCPTool(String toolName, String description,
                   Map<String, Object> parametersDefinition,
                   String[] requiredParameters,
                   McpSyncClient mcpClient, String mcpToolName, boolean directOutput) {
        this.toolName = toolName;
        this.description = description;
        this.parametersDefinition = new HashMap<>(parametersDefinition);
        this.requiredParameters = requiredParameters != null ? requiredParameters.clone() : new String[0];
        this.mcpClient = mcpClient;
        this.mcpToolName = mcpToolName;
        this.directOutput = directOutput;
    }

    /**
     * 构造函数 - 默认不直接输出
     */
    public MCPTool(String toolName, String description,
                   Map<String, Object> parametersDefinition,
                   String[] requiredParameters,
                   McpSyncClient mcpClient, String mcpToolName) {
        this(toolName, description, parametersDefinition, requiredParameters, mcpClient, mcpToolName, false);
    }

    /**
     * 便捷构造函数 - 创建标准的MCP工具
     * @param toolName 工具名称
     * @param description 工具描述
     * @param mcpClient MCP客户端
     * @param mcpToolName MCP工具名称
     * @param directOutput 是否直接输出
     * @return MCPTool实例
     */
    public static MCPTool createStandardMCPTool(String toolName, String description,
                                               McpSyncClient mcpClient, String mcpToolName, boolean directOutput) {
        // 创建通用的参数定义
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new HashMap<>());
        parameters.put("required", new String[0]);

        return new MCPTool(toolName, description, parameters, new String[0], mcpClient, mcpToolName, directOutput);
    }

    @Override
    public String getToolName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParametersDefinition() {
        return new HashMap<>(parametersDefinition);
    }

    @Override
    public String[] getRequiredParameters() {
        return requiredParameters.clone();
    }

    @Override
    protected Mono<ToolResult> doExecute(ToolCall toolCall, ToolContext context, Map<String, Object> arguments) {
        try {
            log.debug("Executing MCP tool {} with arguments: {}", mcpToolName, arguments);

            // 执行MCP工具调用
            return executeMCPCall(toolCall.getId(), arguments, context)
                    .onErrorMap(this::mapToAgentException);

        } catch (Exception e) {
            log.error("Error executing MCP tool call", e);
            return Mono.just(error(toolCall.getId(), "Failed to call MCP tool: " + e.getMessage()));
        }
    }

    /**
     * 执行MCP工具调用
     */
    private Mono<ToolResult> executeMCPCall(String toolCallId, Map<String, Object> args, ToolContext toolContext) {
        return Mono.fromCallable(() -> {
            try {
                log.info("开始调用MCP工具: {}, 参数: {}", mcpToolName, args);

                // 构建MCP工具调用请求
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(mcpToolName, args);
                log.debug("构建MCP请求: {}", request);

                // 调用MCP工具
                log.info("正在调用MCP工具: {}", mcpToolName);
                McpSchema.CallToolResult result = mcpClient.callTool(request);
                log.info("MCP工具调用完成: {}, 结果: {}", mcpToolName, result != null ? "成功" : "失败");

                // 检查是否有错误
                if (Boolean.TRUE.equals(result.isError())) {
                    String errorMsg = extractErrorMessage(result);
                    log.error("MCP工具调用返回错误: {}", errorMsg);
                    throw new AgentException(ErrorCode.TOOL_EXECUTION_FAILED,
                        "MCP tool call failed: " + errorMsg);
                }

                // 提取内容
                String content = extractContent(result);
                log.debug("MCP工具返回内容长度: {}", content != null ? content.length() : 0);

                if (directOutput) {
                    // 直接输出给用户的响应
                    MCPToolResponse mcpResponse = MCPToolResponse.success(
                        toolCallId, mcpToolName, content)
                        .withDirectOutput();
                    return createSpecialToolResult(mcpResponse);
                } else {
                    // 普通响应
                    return success(toolCallId, content);
                }
            } catch (Exception e) {
                log.error("MCP工具调用异常: {}, 错误类型: {}, 错误消息: {}",
                    mcpToolName, e.getClass().getSimpleName(), e.getMessage());

                // 特殊处理超时异常
                if (e.getMessage() != null && e.getMessage().contains("SocketTimeoutException")) {
                    log.error("MCP工具调用超时，可能是服务器响应过慢或网络问题");
                    throw new AgentException(ErrorCode.TOOL_TIMEOUT,
                        "MCP tool call timeout: " + mcpToolName + " - 服务器响应超时，请稍后重试", e);
                }
                throw new AgentException(ErrorCode.TOOL_EXECUTION_FAILED,
                    "MCP tool call failed: " + e.getMessage(), e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic()) // 在IO线程池执行
        .timeout(Duration.ofSeconds(180)) // 增加超时时间到3分钟
        .onErrorMap(error -> {
            if (error instanceof java.util.concurrent.TimeoutException) {
                log.error("MCP工具调用整体超时: {}", mcpToolName);
                return new AgentException(ErrorCode.TOOL_TIMEOUT,
                    "MCP tool call timeout: " + mcpToolName + " - 整体调用超时", error);
            }
            if (!(error instanceof AgentException)) {
                log.error("MCP工具调用未知错误: {}, 错误: {}", mcpToolName, error.getMessage());
                return new AgentException(ErrorCode.TOOL_EXECUTION_FAILED,
                    "MCP tool call failed for: " + mcpToolName, error);
            }
            return error;
        })
        .onErrorResume(error -> {
            log.error("MCP工具调用最终失败: {}, 返回错误结果", mcpToolName);
            return Mono.just(ToolResult.error(toolCallId, "MCP tool call failed: " + error.getMessage()));
        });
    }

    /**
     * 流式MCP工具调用
     */
    @Override
    public Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext toolContext) {
        try {
            Map<String, Object> arguments = parseArguments(toolCall);
            log.debug("Executing MCP tool {} (stream) with arguments: {}", mcpToolName, arguments);

            // 验证参数
            if (!validateParameters(arguments)) {
                return Flux.just(MCPToolResponse.error(toolCall.getId(), "Invalid parameters"));
            }

            log.debug("MCPTool directOutput={}, calling MCP tool {} with args: {}", directOutput, mcpToolName, arguments);

            // 1. 先输出判定片段
            MCPToolResponse judge = MCPToolResponse.createDecisionFragment(toolCall.getId(), directOutput);

            // 2. 再输出实际内容 - 调用MCP工具的流式方法
            Flux<MCPToolResponse> contentFlux = callMCPToolStream(toolCall.getId(), arguments)
                .doOnNext(response -> log.debug("MCP tool {} response: {}", mcpToolName, response.getContent()))
                .map(response -> {
                    if (directOutput) {
                        response.setDirectOutput(true);
                        log.debug("Setting directOutput=true for stream content: {}", response.getContent());
                    }
                    return response;
                })
                .onErrorMap(this::mapToAgentException)
                .onErrorReturn(MCPToolResponse.error(toolCall.getId(), "MCP stream call failed"));

            return Flux.concat(Flux.just(judge), contentFlux)
                .doOnNext(response -> log.debug("MCPTool stream output: type={}, directOutput={}, content={}",
                    response.getResponseType(), response.isDirectOutput(), response.getContent()))
                .cast(BaseToolResponse.class);

        } catch (Exception e) {
            log.error("Error executing MCP tool call (stream)", e);
            return Flux.just(MCPToolResponse.error(toolCall.getId(), "Failed to call MCP tool: " + e.getMessage()));
        }
    }

    /**
     * 调用MCP工具的流式方法
     */
    private Flux<MCPToolResponse> callMCPToolStream(String toolCallId, Map<String, Object> args) {
        return Flux.<MCPToolResponse>create(sink -> {
            try {
                // MCP目前不支持流式调用，使用普通调用并模拟流式输出
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(mcpToolName, args);
                McpSchema.CallToolResult result = mcpClient.callTool(request);
                if (Boolean.TRUE.equals(result.isError())) {
                    sink.next(MCPToolResponse.error(toolCallId, extractErrorMessage(result)));
                } else {
                    String content = extractContent(result);
                    sink.next(MCPToolResponse.streamContent(toolCallId, content));
                }
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 从CallToolResult中提取内容
     */
    private String extractContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                contentBuilder.append(textContent.text());
            } else if (content instanceof McpSchema.ImageContent imageContent) {
                contentBuilder.append("[Image: ").append(imageContent.data()).append("]");
            } else {
                contentBuilder.append(content.toString());
            }
            contentBuilder.append("\n");
        }

        return contentBuilder.toString().trim();
    }

    /**
     * 从CallToolResult中提取错误信息
     */
    private String extractErrorMessage(McpSchema.CallToolResult result) {
        return extractContent(result);
    }

    /**
     * 创建特殊的ToolResult，用于标识直接输出
     */
    private ToolResult createSpecialToolResult(MCPToolResponse response) {
        try {
            // 直接返回 JSON 格式的 MCPToolResponse，不需要特殊标记
            String jsonContent = objectMapper.writeValueAsString(response);
            return ToolResult.success(response.getToolCallId(), jsonContent);
        } catch (Exception e) {
            log.error("Failed to serialize MCPToolResponse", e);
            return ToolResult.error(response.getToolCallId(), "Failed to create special tool result");
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
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return new AgentException(
                ErrorCode.TOOL_TIMEOUT,
                "MCP tool execution timeout", throwable);
        }

        if (throwable instanceof SecurityException) {
            return new AgentException(
                ErrorCode.TOOL_PERMISSION_DENIED,
                "Permission denied for MCP tool", throwable);
        }

        // 默认映射为工具执行失败
        return new AgentException(
            ErrorCode.TOOL_EXECUTION_FAILED,
            "MCP tool execution failed", throwable);
    }

    /**
     * 获取MCP客户端
     */
    public McpSyncClient getMcpClient() {
        return mcpClient;
    }

    /**
     * 获取MCP工具名称
     */
    public String getMcpToolName() {
        return mcpToolName;
    }

    /**
     * 是否直接输出给用户
     */
    public boolean isDirectOutput() {
        return directOutput;
    }
}