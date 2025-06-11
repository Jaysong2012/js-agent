package cn.apmen.jsagent.framework.execution;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行上下文
 * 负责管理Agent执行过程中的状态和元数据
 */
@Data
@Builder
public class ExecutionContext {
    
    /**
     * 执行ID
     */
    private String executionId;
    
    /**
     * Agent ID
     */
    private String agentId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 执行开始时间
     */
    @Builder.Default
    private LocalDateTime startTime = LocalDateTime.now();
    
    /**
     * 当前轮次
     */
    @Builder.Default
    private int currentRound = 1;
    
    /**
     * 最大轮次
     */
    @Builder.Default
    private int maxRounds = 10;
    
    /**
     * 执行状态
     */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.RUNNING;
    
    /**
     * 执行元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new ConcurrentHashMap<>();
    
    /**
     * 执行指标
     */
    @Builder.Default
    private ExecutionMetrics metrics = new ExecutionMetrics();
    
    /**
     * 递增轮次
     */
    public int incrementRound() {
        return ++currentRound;
    }
    
    /**
     * 检查是否达到最大轮次
     */
    public boolean isMaxRoundsReached() {
        return currentRound >= maxRounds;
    }
    
    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        RUNNING,        // 运行中
        COMPLETED,      // 已完成
        FAILED,         // 失败
        TERMINATED      // 已终止
    }
    
    /**
     * 执行指标
     */
    @Data
    public static class ExecutionMetrics {
        private int totalTokensUsed = 0;
        private int toolCallsCount = 0;
        private int llmCallsCount = 0;
        private long executionTimeMs = 0;
        
        public void incrementTokensUsed(int tokens) {
            totalTokensUsed += tokens;
        }
        
        public void incrementToolCalls() {
            toolCallsCount++;
        }
        
        public void incrementLlmCalls() {
            llmCallsCount++;
        }
        
        public void setExecutionTime(long startTime) {
            executionTimeMs = System.currentTimeMillis() - startTime;
        }
    }
}