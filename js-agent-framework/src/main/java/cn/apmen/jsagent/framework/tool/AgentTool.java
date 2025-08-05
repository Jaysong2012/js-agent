package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.agent.Agent;
import cn.apmen.jsagent.framework.agent.WorkerAgent;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent工具 - 将Agent包装为工具
 * 继承AbstractToolExecutor，符合新的工具设计规范
 * 同时实现StreamingToolExecutor以支持流式执行
 */
@Slf4j
public class AgentTool extends AbstractToolExecutor implements StreamingToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 目标Agent - 实际执行功能的Agent
    private final Agent targetAgent;
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
     * @param targetAgent 目标Agent
     * @param directOutput 是否直接输出给用户
     */
    public AgentTool(String toolName, String description,
                    Map<String, Object> parametersDefinition,
                    String[] requiredParameters,
                    Agent targetAgent, boolean directOutput) {
        this.toolName = toolName;
        this.description = description;
        this.parametersDefinition = new HashMap<>(parametersDefinition);
        this.requiredParameters = requiredParameters != null ? requiredParameters.clone() : new String[0];
        this.targetAgent = targetAgent;
        this.directOutput = directOutput;
    }

    /**
     * 构造函数 - 默认不直接输出
     */
    public AgentTool(String toolName, String description,
                    Map<String, Object> parametersDefinition,
                    String[] requiredParameters,
                    Agent targetAgent) {
        this(toolName, description, parametersDefinition, requiredParameters, targetAgent, false);
    }

    /**
     * 便捷构造函数 - 创建标准的Agent工具
     * @param toolName 工具名称
     * @param description 工具描述
     * @param targetAgent 目标Agent
     * @param directOutput 是否直接输出
     * @return AgentTool实例
     */
    public static AgentTool createStandardAgentTool(String toolName, String description,
                                                   Agent targetAgent, boolean directOutput) {
        // 创建标准的参数定义
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // message 参数定义
        Map<String, Object> messageProperty = new HashMap<>();
        messageProperty.put("type", "string");
        messageProperty.put("description", "要发送给Agent的消息内容");
        properties.put("message", messageProperty);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"message"});

        return new AgentTool(toolName, description, parameters, new String[]{"message"}, targetAgent, directOutput);
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
            log.debug("Executing agent call with arguments: {}", arguments);

            // 如果是WorkerAgent，传入ToolContext
            if (targetAgent instanceof WorkerAgent) {
                ((WorkerAgent) targetAgent).withContext(context);
            }

            // 构建消息
            String message = buildMessageFromArgs(arguments);

            // 调用Agent
            return targetAgent.call(message)
                    .map(response -> {
                        String content = response.getContent() != null ? response.getContent() : "";
                        if (directOutput) {
                            // 直接输出给用户的响应
                            AgentToolResponse agentResponse = AgentToolResponse.success(
                                toolCall.getId(), targetAgent.getId(), targetAgent.getName(), content)
                                .withDirectOutput();
                            return createSpecialToolResult(agentResponse);
                        } else {
                            // 普通响应
                            return success(toolCall.getId(), content);
                        }
                    })
                    .onErrorResume(error -> {
                        log.error("Agent call failed for {}: {}", targetAgent.getName(), error.getMessage());
                        return Mono.just(error(toolCall.getId(), "Agent call failed: " + error.getMessage()));
                    });

        } catch (Exception e) {
            log.error("Error executing agent call", e);
            return Mono.just(error(toolCall.getId(), "Failed to call agent: " + e.getMessage()));
        }
    }

    /**
     * 流式Agent工具调用
     */
    @Override
    public Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext toolContext) {
        try {
            Map<String, Object> arguments = parseArguments(toolCall);
            log.debug("Executing agent call (stream) with arguments: {}", arguments);

            // 验证参数
            if (!validateParameters(arguments)) {
                return Flux.just(AgentToolResponse.error(toolCall.getId(), "Invalid parameters"));
            }

            // 如果是WorkerAgent，传入ToolContext
            if (targetAgent instanceof WorkerAgent) {
                ((WorkerAgent) targetAgent).withContext(toolContext);
            }

            String message = buildMessageFromArgs(arguments);
            log.debug("AgentTool directOutput={}, calling targetAgent with message: {}", directOutput, message);

            // 1. 先输出判定片段
            AgentToolResponse judge = AgentToolResponse.createDecisionFragment(toolCall.getId(), directOutput);

            // 2. 再输出实际内容 - 调用Agent的callStream方法
            Flux<AgentToolResponse> contentFlux = targetAgent.callStream(message)
                .doOnNext(response -> log.debug("WorkerAgent {} response: {}", targetAgent.getName(), response.getContent()))
                .filter(response -> response.getType() == AgentResponse.ResponseType.TEXT)
                .map(response -> {
                    AgentToolResponse toolResponse = AgentToolResponse.streamContent(toolCall.getId(), response.getContent());
                    if (directOutput) {
                        toolResponse.setDirectOutput(true);
                        log.debug("Setting directOutput=true for stream content: {}", response.getContent());
                    }
                    return toolResponse;
                })
                .onErrorMap(this::mapToAgentException)
                .onErrorReturn(AgentToolResponse.error(toolCall.getId(), "Stream call failed"));

            return Flux.concat(Flux.just(judge), contentFlux)
                .doOnNext(response -> log.debug("AgentTool stream output: type={}, directOutput={}, content={}",
                    response.getResponseType(), response.isDirectOutput(), response.getContent()))
                .cast(BaseToolResponse.class);

        } catch (Exception e) {
            log.error("Error executing agent call (stream)", e);
            return Flux.just(AgentToolResponse.error(toolCall.getId(), "Failed to call agent: " + e.getMessage()));
        }
    }

    /**
     * 从参数构建消息 - 可以被子类重写以支持不同的参数结构
     */
    protected String buildMessageFromArgs(Map<String, Object> args) {
        // 默认查找 "message" 参数
        String message = getStringParameter(args, "message");
        if (message != null) {
            return message;
        }

        // 如果没有message参数，将所有参数转换为字符串
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 创建特殊的ToolResult，用于标识直接输出
     */
    private ToolResult createSpecialToolResult(AgentToolResponse response) {
        try {
            // 直接返回 JSON 格式的 AgentToolResponse，不需要特殊标记
            String jsonContent = objectMapper.writeValueAsString(response);
            return ToolResult.success(response.getToolCallId(), jsonContent);
        } catch (Exception e) {
            log.error("Failed to serialize AgentToolResponse", e);
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
                "Agent tool execution timeout", throwable);
        }

        if (throwable instanceof SecurityException) {
            return new AgentException(
                ErrorCode.TOOL_PERMISSION_DENIED,
                "Permission denied for agent tool", throwable);
        }

        // 默认映射为工具执行失败
        return new AgentException(
            ErrorCode.TOOL_EXECUTION_FAILED,
            "Agent tool execution failed", throwable);
    }

    /**
     * 获取目标Agent
     */
    public Agent getTargetAgent() {
        return targetAgent;
    }

    /**
     * 是否直接输出给用户
     */
    public boolean isDirectOutput() {
        return directOutput;
    }
}