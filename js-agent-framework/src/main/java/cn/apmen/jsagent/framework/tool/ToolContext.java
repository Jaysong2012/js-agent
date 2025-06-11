package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.core.RunnerContext;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文
 * 为工具执行提供必要的上下文信息
 */
@Data
@Builder
public class ToolContext {

    /**
     * 运行上下文
     */
    private RunnerContext runnerContext;

    /**
     * 当前轮次
     */
    private int currentRound;
    /**
     * 工具调用ID
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 工具执行的额外参数
     */
    @Builder.Default
    private Map<String, Object> parameters = new ConcurrentHashMap<>();

    /**
     * 工具执行的共享数据
     */
    @Builder.Default
    private Map<String, Object> sharedData = new ConcurrentHashMap<>();

    /**
     * 获取用户ID
     */
    public String getUserId() {
        return runnerContext != null ? runnerContext.getUserId() : null;
    }

    /**
     * 获取会话ID
     */
    public String getConversationId() {
        return runnerContext != null ? runnerContext.getConversationId() : null;
    }

    /**
     * 获取执行ID
     */
    public String getExecutionId() {
        return runnerContext != null ? runnerContext.getExecutionId() : null;
    }

    /**
     * 设置参数
     */
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }

    /**
     * 获取参数
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * 设置共享数据
     */
    public void setSharedData(String key, Object value) {
        sharedData.put(key, value);
    }

    /**
     * 获取共享数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key, Class<T> type) {
        Object value = sharedData.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
}