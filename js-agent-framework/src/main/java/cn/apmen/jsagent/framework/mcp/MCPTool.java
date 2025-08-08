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
 *
 * 支持多工具模式：一个MCPTool实例可以调用MCP服务器中的任意工具
 */
@Slf4j
public class MCPTool extends AbstractToolExecutor implements StreamingToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // MCP客户端 - 实际执行功能的客户端
    private final McpSyncClient mcpClient;
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
     * 构造函数 - 多工具模式，支持调用MCP服务器中的任意工具
     * @param mcpClient MCP客户端
     * @param toolName 工具名称
     * @param description 工具描述
     */
    public MCPTool(McpSyncClient mcpClient, String toolName, String description) {
        this.mcpClient = mcpClient;
        this.toolName = toolName;
        this.description = description;

        // 创建通用的参数定义，支持动态工具调用
        this.parametersDefinition = createDynamicParametersDefinition();
        this.requiredParameters = new String[]{"tool_name", "arguments"};
    }

    /**
     * 创建动态参数定义，支持调用MCP服务器中的任意工具
     */
    private Map<String, Object> createDynamicParametersDefinition() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // tool_name参数 - 指定要调用的具体工具
        Map<String, Object> toolNameParam = new HashMap<>();
        toolNameParam.put("type", "string");
        toolNameParam.put("description", "要调用的具体工具名称，例如：bing_search, maps_geo, datetime_current_time等");
        properties.put("tool_name", toolNameParam);

        // arguments参数 - 工具的具体参数
        Map<String, Object> argumentsParam = new HashMap<>();
        argumentsParam.put("type", "object");
        argumentsParam.put("description", "工具的具体参数对象，例如：{\"query\": \"搜索关键词\", \"top_k\": 3}");
        argumentsParam.put("additionalProperties", true);
        // 添加示例
        Map<String, Object> example = new HashMap<>();
        example.put("query", "搜索关键词");
        example.put("top_k", 3);
        argumentsParam.put("example", example);
        properties.put("arguments", argumentsParam);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"tool_name"});  // arguments不是必需的，可以为空对象

        return parameters;
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
            log.debug("Executing MCP tool {} with arguments: {}", toolName, objectMapper.writeValueAsString(arguments));

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
                // 从参数中提取工具名称和参数
                String actualToolName = (String) args.get("tool_name");
                Object argumentsRaw = args.get("arguments");

                log.debug("原始arguments参数: {}, 类型: {}", argumentsRaw,
                    argumentsRaw != null ? argumentsRaw.getClass().getSimpleName() : "null");

                Map<String, Object> actualArgs = parseArgumentsParameter(argumentsRaw);

                if (actualToolName == null) {
                    throw new IllegalArgumentException("tool_name parameter is required for MCP tool call");
                }
                if (actualArgs == null) {
                    actualArgs = new HashMap<>();
                }

                log.info("开始调用MCP工具: {}, 参数: {}", actualToolName, actualArgs);

                // 构建MCP工具调用请求
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(actualToolName, actualArgs);
                log.debug("构建MCP请求: {}", request);

                // 调用MCP工具
                log.info("正在调用MCP工具: {}", actualToolName);
                McpSchema.CallToolResult result = mcpClient.callTool(request);
                log.info("MCP工具调用完成: {}, 结果: {}", actualToolName, result != null ? "成功" : "失败");

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

                return success(toolCallId, content);
            } catch (Exception e) {
                log.error("MCP工具调用异常: {}, 错误类型: {}, 错误消息: {}",
                    toolName, e.getClass().getSimpleName(), e.getMessage());

                // 特殊处理超时异常
                if (e.getMessage() != null && e.getMessage().contains("SocketTimeoutException")) {
                    log.error("MCP工具调用超时，可能是服务器响应过慢或网络问题");
                    throw new AgentException(ErrorCode.TOOL_TIMEOUT,
                        "MCP tool call timeout: " + toolName + " - 服务器响应超时，请稍后重试", e);
                }
                throw new AgentException(ErrorCode.TOOL_EXECUTION_FAILED,
                    "MCP tool call failed: " + e.getMessage(), e);
            }
        })
        .subscribeOn(Schedulers.boundedElastic()) // 在IO线程池执行
        .timeout(Duration.ofSeconds(180)) // 增加超时时间到3分钟
        .onErrorMap(error -> {
            if (error instanceof java.util.concurrent.TimeoutException) {
                log.error("MCP工具调用整体超时: {}", toolName);
                return new AgentException(ErrorCode.TOOL_TIMEOUT,
                    "MCP tool call timeout: " + toolName + " - 整体调用超时", error);
            }
            if (!(error instanceof AgentException)) {
                log.error("MCP工具调用未知错误: {}, 错误: {}", toolName, error.getMessage());
                return new AgentException(ErrorCode.TOOL_EXECUTION_FAILED,
                    "MCP tool call failed for: " + toolName, error);
            }
            return error;
        })
        .onErrorResume(error -> {
            log.error("MCP工具调用最终失败: {}, 返回错误结果", toolName);
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
            log.debug("Executing MCP tool {} (stream) with arguments: {}", toolName, arguments);

            // 验证参数
            if (!validateParameters(arguments)) {
                return Flux.just(MCPToolResponse.error(toolCall.getId(), "Invalid parameters"));
            }

            log.debug("MCPTool calling MCP tool {} with args: {}", toolName, arguments);

            // 1. 先输出判定片段
            MCPToolResponse judge = MCPToolResponse.createDecisionFragment(toolCall.getId());

            // 2. 再输出实际内容 - 调用MCP工具的流式方法
            Flux<MCPToolResponse> contentFlux = callMCPToolStream(toolCall.getId(), arguments)
                .doOnNext(response -> log.debug("MCP tool {} response: {}", toolName, response.getContent()))
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
                // 从参数中提取工具名称和参数
                String actualToolName = (String) args.get("tool_name");
                Map<String, Object> actualArgs = parseArgumentsParameter(args.get("arguments"));

                if (actualToolName == null) {
                    sink.error(new IllegalArgumentException("tool_name parameter is required for MCP tool call"));
                    return;
                }
                if (actualArgs == null) {
                    actualArgs = new HashMap<>();
                }

                // MCP目前不支持流式调用，使用普通调用并模拟流式输出
                McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(actualToolName, actualArgs);
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
     * 解析arguments参数，支持字符串和Map两种格式
     */
    private Map<String, Object> parseArgumentsParameter(Object argumentsObj) {
        if (argumentsObj == null) {
            return new HashMap<>();
        }

        if (argumentsObj instanceof Map) {
            // 如果已经是Map，直接返回
            return (Map<String, Object>) argumentsObj;
        }

        if (argumentsObj instanceof String) {
            // 如果是字符串，尝试解析为JSON
            String argumentsStr = (String) argumentsObj;
            if (argumentsStr.trim().isEmpty()) {
                return new HashMap<>();
            }

            try {
                return objectMapper.readValue(argumentsStr, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse arguments string as JSON: {}, error: {}", argumentsStr, e.getMessage());
                // 如果解析失败，返回空Map
                return new HashMap<>();
            }
        }

        log.warn("Unsupported arguments type: {}, returning empty map", argumentsObj.getClass());
        return new HashMap<>();
    }

    /**
     * 获取MCP客户端
     */
    public McpSyncClient getMcpClient() {
        return mcpClient;
    }

}