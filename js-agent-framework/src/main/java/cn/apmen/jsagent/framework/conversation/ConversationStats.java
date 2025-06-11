package cn.apmen.jsagent.framework.conversation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话统计信息
 */
@Data
@Builder
public class ConversationStats {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 消息总数
     */
    private int totalMessages;
    
    /**
     * 用户消息数
     */
    private int userMessages;
    
    /**
     * 助手消息数
     */
    private int assistantMessages;
    
    /**
     * 系统消息数
     */
    private int systemMessages;
    
    /**
     * 工具消息数
     */
    private int toolMessages;
    
    /**
     * 总token数（估算）
     */
    private int totalTokens;
    
    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdatedAt;
    
    /**
     * 会话持续时间（分钟）
     */
    private long durationMinutes;
    
    /**
     * 是否已压缩
     */
    private boolean compressed;
    
    /**
     * 压缩前的消息数
     */
    private Integer originalMessageCount;
}