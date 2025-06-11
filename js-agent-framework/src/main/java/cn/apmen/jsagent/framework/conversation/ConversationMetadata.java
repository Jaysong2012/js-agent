package cn.apmen.jsagent.framework.conversation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 会话元数据
 */
@Data
@Builder
public class ConversationMetadata {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话标题
     */
    private String title;
    
    /**
     * 会话描述
     */
    private String description;
    
    /**
     * 会话标签
     */
    private java.util.Set<String> tags;
    
    /**
     * 自定义属性
     */
    private Map<String, Object> customProperties;
    
    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveAt;
    
    /**
     * 会话状态
     */
    private ConversationStatus status;
    
    /**
     * 关联的Agent ID
     */
    private String agentId;
    
    /**
     * 会话优先级
     */
    private ConversationPriority priority;
    
    /**
     * 是否置顶
     */
    private boolean pinned;
    
    /**
     * 会话状态枚举
     */
    public enum ConversationStatus {
        ACTIVE,     // 活跃
        INACTIVE,   // 非活跃
        ARCHIVED,   // 已归档
        DELETED     // 已删除
    }
    
    /**
     * 会话优先级枚举
     */
    public enum ConversationPriority {
        LOW,        // 低优先级
        NORMAL,     // 普通优先级
        HIGH,       // 高优先级
        URGENT      // 紧急
    }
}