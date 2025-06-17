package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.agent.Agent;
import cn.apmen.jsagent.framework.agent.WorkerAgent;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Agent工具 - 继承Tool，作为代理注册到CoreAgent
 * 实际功能由WorkerAgent实现，AgentTool只是代理层
 *
 * 使用示例：
 * <pre>
 * // 1. 创建WorkerAgent
 * WorkerAgent mathAgent = WorkerAgent.builder()
 *     .id("math-agent")
 *     .name("数学助手")
 *     .systemPrompt("你是一个数学专家，专门解决数学问题")
 *     .llmClient(openAIClient)
 *     .build();
 *
 * // 2. 用户定义Tool结构（参考AgentConfig的方式）
 * Tool toolDefinition = new Tool();
 * toolDefinition.setType("function");
 * Function function = new Function();
 * function.setName("call_math_agent");
 * function.setDescription("调用数学专家Agent来解决数学问题");
 * // ... 设置parameters等
 * toolDefinition.setFunction(function);
 *
 * // 3. 创建AgentTool
 * AgentTool agentTool = new AgentTool(toolDefinition, mathAgent, true); // true表示直接输出给用户
 *
 * // 4. 将AgentTool添加到CoreAgent的tools列表
 * CoreAgent coreAgent = CoreAgent.builder()
 *     .tools(List.of(agentTool))
 *     .build();
 * </pre>
 */
@Slf4j
public class AgentTool extends Tool implements StreamingToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 配置ObjectMapper支持Java 8时间类型
    {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    // 目标Agent - 实际执行功能的Agent
    private final Agent targetAgent;
    // 是否直接输出给用户
    private final boolean directOutput;
    /**
     * 构造函数
     * @param toolDefinition 用户定义的Tool结构
     * @param targetAgent 目标Agent
     * @param directOutput 是否直接输出给用户
     */
    public AgentTool(Tool toolDefinition, Agent targetAgent, boolean directOutput) {
        super(toolDefinition.getType(), toolDefinition.getFunction());
        this.targetAgent = targetAgent;
        this.directOutput = directOutput;
    }
    /**
     * 构造函数 - 默认不直接输出
     */
    public AgentTool(Tool toolDefinition, Agent targetAgent) {
        this(toolDefinition, targetAgent, false);
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
            log.debug("Executing agent call with arguments: {}", arguments);
            // 解析参数 - 具体参数结构由用户定义的Tool决定
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            // 执行Agent调用
            return executeAgentCall(toolCall.getId(), args, toolContext)
                    .onErrorMap(this::mapToAgentException);

        } catch (AgentException e) {
            return Mono.just(ToolResult.error(toolCall.getId(), e.getMessage()));
        } catch (Exception e) {
            log.error("Error executing agent call", e);
            AgentException agentException = new AgentException(
                ErrorCode.TOOL_EXECUTION_FAILED,
                "Failed to call agent", e);
            return Mono.just(ToolResult.error(toolCall.getId(), agentException.getMessage()));
        }
    }

    /**
     * 执行Agent调用
     */
    private Mono<ToolResult> executeAgentCall(String toolCallId, Map<String, Object> args, ToolContext toolContext) {
        // 如果是WorkerAgent，传入ToolContext
        if (targetAgent instanceof WorkerAgent) {
            ((WorkerAgent) targetAgent).withContext(toolContext);
        }
        // 构建消息 - 这里可以根据具体的参数结构来构建
        String message = buildMessageFromArgs(args);
        // 调用Agent的call方法
        return targetAgent.call(message)
                .map(response -> {
                    String content = response.getContent() != null ? response.getContent() : "";
                    if (directOutput) {
                        // 直接输出给用户的响应
                        AgentToolResponse agentResponse = AgentToolResponse.success(
                            toolCallId, targetAgent.getId(), targetAgent.getName(), content)
                            .withDirectOutput();
                        return createSpecialToolResult(agentResponse);
                    } else {
                        // 普通响应
                        AgentToolResponse agentResponse = AgentToolResponse.success(
                            toolCallId, targetAgent.getId(), targetAgent.getName(), content);
                        return agentResponse.toToolResult();
                    }
                })
                .onErrorMap(error -> new AgentException(
                    ErrorCode.AGENT_EXECUTION_FAILED,
                    "Agent call failed for: " + targetAgent.getName(), error))
                .onErrorReturn(ToolResult.error(toolCallId, "Agent call failed"));
    }

    /**
     * 从参数构建消息 - 可以被子类重写以支持不同的参数结构
     */
    protected String buildMessageFromArgs(Map<String, Object> args) {
        // 默认查找 "message" 参数
        Object messageObj = args.get("message");
        if (messageObj != null) {
            return messageObj.toString();
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
     * 流式Agent工具调用
     */
    @Override
    public Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext toolContext) {
        try {
            String arguments = toolCall.getFunction().getArguments();
            log.debug("Executing agent call (stream) with arguments: {}", arguments);
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            // 如果是WorkerAgent，传入ToolContext
            if (targetAgent instanceof WorkerAgent) {
                ((WorkerAgent) targetAgent).withContext(toolContext);
            }
            String message = buildMessageFromArgs(args);
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