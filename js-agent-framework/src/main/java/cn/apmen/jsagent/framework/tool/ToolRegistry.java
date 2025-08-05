package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 工具注册器，管理所有可用的工具执行器
 * 提供工具注册、查询、执行等核心功能
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
     * 获取所有工具定义列表
     * @return 所有注册工具的定义列表
     */
    public List<ToolDefinition> getAllToolDefinitions() {
        return executors.values().stream()
                .map(executor -> (ToolDefinition) executor)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定工具名称的工具定义列表
     * @param toolNames 指定的工具名称列表
     * @return 指定工具的定义列表
     */
    public List<ToolDefinition> getToolDefinitions(List<String> toolNames) {
        return toolNames.stream()
                .map(executors::get)
                .filter(Objects::nonNull)
                .map(executor -> (ToolDefinition) executor)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有工具的Tool对象列表
     * @return 所有工具的Tool对象列表
     */
    public List<Tool> getAllTools() {
        return getAllToolDefinitions().stream()
                .map(ToolDefinition::buildTool)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定工具名称的Tool对象列表
     * @param toolNames 指定的工具名称列表
     * @return 指定工具的Tool对象列表
     */
    public List<Tool> getTools(List<String> toolNames) {
        return getToolDefinitions(toolNames).stream()
                .map(ToolDefinition::buildTool)
                .collect(Collectors.toList());
    }

    /**
     * 根据工具名称模式匹配获取工具
     * @param pattern 工具名称模式（支持通配符*）
     * @return 匹配的工具列表
     */
    public List<Tool> getToolsByPattern(String pattern) {
        String regex = pattern.replace("*", ".*");
        return executors.keySet().stream()
                .filter(name -> name.matches(regex))
                .map(this::getToolDefinition)
                .filter(Objects::nonNull)
                .map(ToolDefinition::buildTool)
                .collect(Collectors.toList());
    }

    /**
     * 将工具定义列表转换为Tool对象列表
     * @param toolDefinitions 工具定义列表
     * @return Tool对象列表
     */
    public static List<Tool> convertToTools(List<ToolDefinition> toolDefinitions) {
        return toolDefinitions.stream()
                .map(ToolDefinition::buildTool)
                .collect(Collectors.toList());
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

    /**
     * 获取工具定义
     */
    public ToolDefinition getToolDefinition(String toolName) {
        return executors.get(toolName);
    }

    /**
     * 获取注册的工具数量
     */
    public int getToolCount() {
        return executors.size();
    }

    /**
     * 清空所有注册的工具
     */
    public void clear() {
        executors.clear();
        log.info("Cleared all registered tools");
    }

    /**
     * 移除指定工具
     */
    public boolean removeExecutor(String toolName) {
        ToolExecutor removed = executors.remove(toolName);
        if (removed != null) {
            log.info("Removed tool executor: {}", toolName);
            return true;
        }
        return false;
    }

    /**
     * 获取工具统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTools", executors.size());
        stats.put("toolNames", new ArrayList<>(executors.keySet()));
        return stats;
    }
}