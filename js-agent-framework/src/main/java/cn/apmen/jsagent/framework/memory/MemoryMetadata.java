package cn.apmen.jsagent.framework.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryMetadata {

    /**
     * 会话标题
     */
    private String sessionTitle;

    /**
     * 会话描述
     */
    private String sessionDescription;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 会话标签
     */
    private String[] tags;

    /**
     * 优先级（1-10）
     */
    @Builder.Default
    private int priority = 5;

    /**
     * 是否为重要会话
     */
    @Builder.Default
    private boolean important = false;

    /**
     * 自定义属性
     */
    private Map<String, Object> customProperties;

    /**
     * 记忆保留策略
     */
    @Builder.Default
    private MemoryRetentionPolicy retentionPolicy = MemoryRetentionPolicy.DEFAULT;

    /**
     * 最大记忆大小（消息数量）
     */
    @Builder.Default
    private int maxMemorySize = 1000;

    /**
     * 记忆保留策略枚举
     */
    public enum MemoryRetentionPolicy {
        /**
         * 默认策略：保留最近的消息
         */
        DEFAULT,

        /**
         * 重要消息优先：保留标记为重要的消息
         */
        IMPORTANT_FIRST,

        /**
         * 智能压缩：自动压缩旧消息
         */
        SMART_COMPRESSION,

        /**
         * 永久保留：不删除任何消息
         */
        PERMANENT,

        /**
         * 定时清理：定期清理旧消息
         */
        SCHEDULED_CLEANUP
    }
}

