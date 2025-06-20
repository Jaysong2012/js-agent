package cn.apmen.jsagent.framework.mcp;

import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
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
import java.util.Map;

/**
 * MCP工具 - 继承Tool，作为代理注册到CoreAgent
 * 实际功能由MCP服务器实现，MCPTool只是代理层
 *
 * 使用示例：
 * <pre>
 * // 1. 创建MCP客户端
 * McpSyncClient mcpClient = McpClient.sync(transport)
 *     .requestTimeout(Duration.ofSeconds(30))
 *     .build();
 *
 * // 2. 用户定义Tool结构
 * Tool toolDefinition = new Tool();
 * toolDefinition.setType("function");
 * Function function = new Function();
 * function.setName("read_file");
 * function.setDescription("读取文件内容");
 * // ... 设置parameters等
 * toolDefinition.setFunction(function);
 *
 * // 3. 创建MCPTool
 * MCPTool mcpTool = new MCPTool(toolDefinition, mcpClient, "read_file", true); // true表示直接输出给用户
 *
 * // 4. 将MCPTool添加到CoreAgent的tools列表
 * CoreAgent coreAgent = CoreAgent.builder()
 *     .tools(List.of(mcpTool))
 *     .build();
 * </pre>
 */
@Slf4j
public class MCPTool extends Tool implements StreamingToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 配置ObjectMapper支持Java 8时间类型
    {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    // MCP客户端 - 实际执行功能的客户端
    private final McpSyncClient mcpClient;
    // MCP工具名称
    private final String mcpToolName;
    // 是否直接输出给用户
    private final boolean directOutput;
    /**
     * 构造函数
     * @param toolDefinition 用户定义的Tool结构
     * @param mcpClient MCP客户端
     * @param mcpToolName MCP工具名称
     * @param directOutput 是否直接输出给用户
     */
    public MCPTool(Tool toolDefinition, McpSyncClient mcpClient, String mcpToolName, boolean directOutput) {
        super(toolDefinition.getType(), toolDefinition.getFunction());
        this.mcpClient = mcpClient;
        this.mcpToolName = mcpToolName;
        this.directOutput = directOutput;
    }
    /**
     * 构造函数 - 默认不直接输出
     */
    public MCPTool(Tool toolDefinition, McpSyncClient mcpClient, String mcpToolName) {
        this(toolDefinition, mcpClient, mcpToolName, false);
    }

    @Override
    public String getToolName() {
        return getFunction().getName();
    }

    @Override
    public String getDescription() {
        return getFunction().getDescription();
    }

    @Override
    public Mono<ToolResult> execute(ToolCall toolCall, ToolContext toolContext) {
        try {
            String arguments = toolCall.getFunction().getArguments();
            log.debug("Executing MCP tool {} with arguments: {}", mcpToolName, arguments);
            // 解析参数 - 具体参数结构由用户定义的Tool决定
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            // 执行MCP工具调用
            return executeMCPCall(toolCall.getId(), args, toolContext)
                    .onErrorMap(this::mapToAgentException);

        } catch (AgentException e) {
            return Mono.just(ToolResult.error(toolCall.getId(), e.getMessage()));
        } catch (Exception e) {
            log.error("Error executing MCP tool call", e);
            AgentException agentException = new AgentException(
                ErrorCode.TOOL_EXECUTION_FAILED,
                "Failed to call MCP tool", e);
            return Mono.just(ToolResult.error(toolCall.getId(), agentException.getMessage()));
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
                    MCPToolResponse mcpResponse = MCPToolResponse.success(
                        toolCallId, mcpToolName, content);
                    return mcpResponse.toToolResult();
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
     * 流式MCP工具调用
     */
    @Override
    public Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext toolContext) {
        try {
            String arguments = toolCall.getFunction().getArguments();
            log.debug("Executing MCP tool {} (stream) with arguments: {}", mcpToolName, arguments);
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            log.debug("MCPTool directOutput={}, calling MCP tool {} with args: {}", directOutput, mcpToolName, args);
            // 1. 先输出判定片段
            MCPToolResponse judge = MCPToolResponse.createDecisionFragment(toolCall.getId(), directOutput);
            // 2. 再输出实际内容 - 调用MCP工具的流式方法
            Flux<MCPToolResponse> contentFlux = callMCPToolStream(toolCall.getId(), args)
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