package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.agent.Agent;
import cn.apmen.jsagent.framework.agent.WorkerAgent;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent工具 - 用于调用其他Agent
 * 支持流式调用和直接输出给用户
 * 通过Agent接口调用各种类型的Agent
 */
@Slf4j
public class AgentTool implements StreamingToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Agent注册表 - 现在使用Agent接口
    private final Map<String, Agent> agentRegistry = new ConcurrentHashMap<>();

    @Override
    public String getToolName() {
        return "call_agent";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public Mono<ToolResult> execute(ToolCall toolCall, ToolContext toolContext) {
        try {
            String arguments = toolCall.getFunction().getArguments();
            log.debug("Executing agent call with arguments: {}", arguments);
            // 解析参数
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            String targetAgentId = (String) args.get("agent_id");
            String message = (String) args.get("message");
            boolean directOutput = Boolean.parseBoolean(args.getOrDefault("direct_output", "false").toString());
            if (targetAgentId == null || message == null) {
                throw new AgentException(
                    ErrorCode.TOOL_INVALID_ARGUMENTS,
                    "Missing required parameters: agent_id and message");
            }
            // 获取目标Agent
            Agent targetAgent = agentRegistry.get(targetAgentId);
            if (targetAgent == null) {
                throw new AgentException(
                    ErrorCode.AGENT_NOT_FOUND,
                    "Agent not found: " + targetAgentId);
            }
            // 执行Agent调用
            return executeAgentCall(toolCall.getId(), targetAgent, message, directOutput, toolContext)
                    .onErrorMap(this::mapToAgentException);

        } catch (AgentException e) {
            return Mono.just(ToolResult.error(toolCall.getId(), e.getMessage()));
        } catch (Exception e) {
            log.error("Error executing agent call", e);
            AgentException agentException =
                new AgentException(
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "Failed to call agent", e);
            return Mono.just(ToolResult.error(toolCall.getId(), agentException.getMessage()));
        }
    }

    /**
     * 执行Agent调用 - 通过Agent接口调用
     */
    private Mono<ToolResult> executeAgentCall(String toolCallId, Agent targetAgent, String message,
                                            boolean directOutput, ToolContext toolContext) {
        // 如果是WorkerAgent，传入ToolContext
        if (targetAgent instanceof WorkerAgent) {
            ((WorkerAgent) targetAgent).withContext(toolContext);
        }
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
     * 创建特殊的ToolResult，用于标识直接输出
     */
    private ToolResult createSpecialToolResult(AgentToolResponse response) {
        try {
            // 在content中添加特殊标记，让AgentRunner知道这是直接输出
            String specialContent = "AGENT_DIRECT_OUTPUT:" + objectMapper.writeValueAsString(response);
            return ToolResult.success(response.getToolCallId(), specialContent);
        } catch (Exception e) {
            log.error("Failed to serialize AgentToolResponse", e);
            return ToolResult.error(response.getToolCallId(), "Failed to create special tool result");
        }
    }

    /**
     * 流式Agent工具调用（新接口）
     * 系统会先消费判定片段，再决定后续流式内容如何处理
     */
    @Override
    public Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext toolContext) {
        try {
            String arguments = toolCall.getFunction().getArguments();
            log.debug("Executing agent call (stream) with arguments: {}", arguments);
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);
            String targetAgentId = (String) args.get("agent_id");
            String message = (String) args.get("message");
            boolean directOutput = Boolean.parseBoolean(args.getOrDefault("direct_output", "false").toString());
            if (targetAgentId == null || message == null) {
                return Flux.just(AgentToolResponse.error(toolCall.getId(), "Missing required parameters: agent_id and message"));
            }
            Agent targetAgent = agentRegistry.get(targetAgentId);
            if (targetAgent == null) {
                return Flux.just(AgentToolResponse.error(toolCall.getId(), "Agent not found: " + targetAgentId));
            }
            // 如果是WorkerAgent，传入ToolContext
            if (targetAgent instanceof WorkerAgent) {
                ((WorkerAgent) targetAgent).withContext(toolContext);
            }
            // 1. 先输出判定片段
            AgentToolResponse judge = AgentToolResponse.createDecisionFragment(toolCall.getId(), directOutput);
            // 2. 再输出实际内容 - 调用Agent的callStream方法
            Flux<AgentToolResponse> contentFlux = targetAgent.callStream(message)
                .filter(response -> response.getType() == AgentResponse.ResponseType.TEXT)
                .map(response -> AgentToolResponse.streamContent(toolCall.getId(), response.getContent()))
                .onErrorMap(this::mapToAgentException)
                .onErrorReturn(AgentToolResponse.error(toolCall.getId(), "Stream call failed"));
            return Flux.concat(Flux.just(judge), contentFlux);
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
     * 注册Agent
     */
    public void registerAgent(Agent agent) {
        agentRegistry.put(agent.getId(), agent);
        log.info("Registered agent: {} ({})", agent.getName(), agent.getId());
    }

    /**
     * 批量注册Agent
     */
    public void registerAgents(Map<String, Agent> agents) {
        agents.forEach((id, agent) -> registerAgent(agent));
    }

    /**
     * 获取已注册的Agent
     */
    public Agent getAgent(String agentId) {
        return agentRegistry.get(agentId);
    }

    /**
     * 获取所有已注册的Agent ID
     */
    public java.util.Set<String> getRegisteredAgentIds() {
        return agentRegistry.keySet();
    }

    /**
     * 检查Agent是否已注册
     */
    public boolean isAgentRegistered(String agentId) {
        return agentRegistry.containsKey(agentId);
    }
}