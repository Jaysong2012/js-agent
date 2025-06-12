package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.conversation.ConversationMetadata;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 上下文信息
 * 包含用户信息、会话信息和环境信息
 */
@Data
@Builder(toBuilder = true)
public class ContextInformation {
    
    private String userId;
    private String conversationId;
    private LocalDateTime loadTime;
    
    private UserInformation userInformation;
    private ConversationInformation conversationInformation;
    private EnvironmentInformation environmentInformation;
    
    /**
     * 用户信息
     */
    @Data
    @Builder
    public static class UserInformation {
        private String userId;
        private String username;
        private String preferredLanguage;
        private String timezone;
        private String userLevel;
        private Map<String, Object> preferences;
        private LocalDateTime lastActiveTime;
        private String userType; // VIP, 普通用户等
    }
    
    /**
     * 会话信息
     */
    @Data
    @Builder
    public static class ConversationInformation {
        private String conversationId;
        private boolean isNewConversation;
        private ConversationMetadata metadata;
        private int messageCount;
        private LocalDateTime lastActiveTime;
        private String conversationTopic; // 会话主题
        private List<String> tags; // 会话标签
    }
    
    /**
     * 环境信息
     */
    @Data
    @Builder
    public static class EnvironmentInformation {
        private LocalDateTime currentTime;
        private String systemVersion;
        private List<String> availableTools;
        private String systemLoad; // 系统负载状态
        private Map<String, Object> systemConfig;
        private String deploymentEnvironment; // dev, test, prod
    }
}