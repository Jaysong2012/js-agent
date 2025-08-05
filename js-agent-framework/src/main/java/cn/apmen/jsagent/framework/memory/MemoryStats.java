package cn.apmen.jsagent.framework.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 记忆统计信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryStats {

    /**
     * 总消息数量
     */
    private int totalMessages;

    /**
     * 用户消息数量
     */
    private int userMessages;

    /**
     * 助手消息数量
     */
    private int assistantMessages;

    /**
     * 工具消息数量
     */
    private int toolMessages;

    /**
     * 系统消息数量
     */
    private int systemMessages;

    /**
     * 估算的总token数
     */
    private int estimatedTokens;

    /**
     * 第一条消息时间
     */
    private LocalDateTime firstMessageTime;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageTime;

    /**
     * 会话持续时间（分钟）
     */
    private long sessionDurationMinutes;

    /**
     * 是否已压缩
     */
    private boolean compressed;

    /**
     * 压缩前的消息数量
     */
    private Integer originalMessageCount;
}

