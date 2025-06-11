package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册器，管理所有可用的工具执行器
 */
@Slf4j
public class ToolRegistry {
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();
    /**
     * 注册工具执行器
     */
    public void registerExecutor(ToolExecutor executor) {
        executors.put(executor.getToolName(), executor);
        log.info("Registered tool executor: {}", executor.getToolName());
    }
    /**
     * 批量注册工具执行器
     */
    public void registerExecutors(List<ToolExecutor> executorList) {
        executorList.forEach(this::registerExecutor);
    }
    /**
     * 执行工具调用（带上下文）
     */
    public Mono<ToolResult> execute(ToolCall toolCall, ToolContext toolContext) {
        if (toolCall.getFunction() == null) {
            return Mono.just(ToolResult.error(toolCall.getId(), "Tool function is null"));
        }
        String toolName = toolCall.getFunction().getName();
        ToolExecutor executor = executors.get(toolName);
        if (executor == null) {
            log.warn("No executor found for tool: {}", toolName);
            return Mono.just(ToolResult.error(toolCall.getId(), "Unknown tool: " + toolName));
        }

        // 更新ToolContext中的工具信息
        if (toolContext != null) {
            toolContext.setToolCallId(toolCall.getId());
            toolContext.setToolName(toolName);
        }

        return executor.execute(toolCall, toolContext)
                .doOnNext(result -> log.debug("Tool {} executed with result: {}", toolName, result.isSuccess()))
                .doOnError(error -> log.error("Error executing tool {}: {}", toolName, error.getMessage()))
                .onErrorReturn(ToolResult.error(toolCall.getId(), "Tool execution failed"));
    }
    /**
     * 执行工具调用（兼容旧版本）
     */
    public Mono<ToolResult> execute(ToolCall toolCall) {
        return execute(toolCall, null);
    }

    /**
     * 检查工具是否存在
     */
    public boolean hasExecutor(String toolName) {
        return executors.containsKey(toolName);
    }
    /**
     * 获取所有注册的工具名称
     */
    public List<String> getRegisteredToolNames() {
        return List.copyOf(executors.keySet());
    }

    /**
     * 获取工具执行器
     */
    public ToolExecutor getExecutor(String toolName) {
        return executors.get(toolName);
    }
}