package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.memory.MemoryService;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主要承载Agent执行上下文
 * 分离MemoryService和ConversationService的职责：
 * - MemoryService: 记录Agent运行中的所有事件和消息（assistant/tool/system等）
 * - ConversationService: 只记录用户可见的对话内容（user/TEXT_RESPONSE）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class RunnerContext {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内存服务 - 记录Agent运行中的所有事件和消息
     */
    private MemoryService memoryService;

    /**
     * 对话服务 - 只记录用户可见的对话内容
     */
    private ConversationService conversationService;

    private String userId;

    private String conversationId;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 当前轮次 - 使用原子操作
     */
    @Builder.Default
    private AtomicInteger currentRound = new AtomicInteger(0);

    /**
     * 最大轮次限制（防止无限循环）
     */
    @Builder.Default
    private int maxRounds = 10;

    /**
     * 最大上下文token数
     */
    @Builder.Default
    private int maxContextTokens = 4000;

    /**
     * 执行开始时间
     */
    @Builder.Default
    private LocalDateTime startTime = LocalDateTime.now();

    /**
     * 执行元数据
     */
    @Builder.Default
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    /**
     * 添加消息到历史记录
     * 所有消息都会记录到MemoryService，只有用户消息会记录到ConversationService
     */
    public void addMessage(Message message) {
        // 异步记录到MemoryService（所有消息）
        if (memoryService != null && conversationId != null) {
            memoryService.addMessage(conversationId, message)
                .doOnSuccess(v -> log.debug("Message recorded to MemoryService: role={}, content={}",
                    message.getRole(),
                    message.getContent() != null ? message.getContent().substring(0, Math.min(50, message.getContent().length())) + "..." : "null"))
                .doOnError(error -> log.warn("Failed to record message to MemoryService", error))
                .subscribe(); // 异步执行，不阻塞主流程
        }

        // 只有用户消息记录到ConversationService
        if ("user".equals(message.getRole()) && conversationService != null && conversationId != null) {
            conversationService.addMessage(conversationId, message)
                .doOnSuccess(v -> log.debug("User message recorded to ConversationService: {}",
                    message.getContent() != null ? message.getContent().substring(0, Math.min(50, message.getContent().length())) + "..." : "null"))
                .doOnError(error -> log.warn("Failed to record user message to ConversationService", error))
                .subscribe();
        }

        log.debug("Message processed: role={}, content={}",
            message.getRole(),
            message.getContent() != null ? message.getContent().substring(0, Math.min(50, message.getContent().length())) + "..." : "null");
    }

    /**
     * 添加用户消息
     */
    public void addUserMessage(String content) {
        addMessage(new Message("user", content));
    }

    /**
     * 添加助手消息
     */
    public void addAssistantMessage(String content) {
        addMessage(new Message("assistant", content));
    }

    /**
     * 添加系统消息
     */
    public void addSystemMessage(String content) {
        addMessage(new Message("system", content));
    }

    /**
     * 添加工具结果消息
     */
    public void addToolMessage(String toolCallId, String content) {
        Message toolMessage = new Message("tool", content, toolCallId);
        addMessage(toolMessage);
    }

    /**
     * 记录用户可见的TEXT_RESPONSE到ConversationService
     * 这是用户实际看到的Agent回复
     */
    public void recordTextResponseToConversation(String content) {
        if (conversationService != null && conversationId != null && content != null && !content.trim().isEmpty()) {
            Message assistantMessage = new Message("assistant", content);
            conversationService.addMessage(conversationId, assistantMessage)
                .doOnSuccess(v -> log.debug("TEXT_RESPONSE recorded to ConversationService: {}",
                    content.substring(0, Math.min(50, content.length())) + "..."))
                .doOnError(error -> log.warn("Failed to record TEXT_RESPONSE to ConversationService", error))
                .subscribe();
        }
    }

    /**
     * 获取完整的消息列表（包含系统提示词）
     * 使用MemoryService的上下文窗口管理
     */
    public List<Message> getCompleteMessageList() {
        List<Message> completeMessages = new ArrayList<>();

        // 添加系统提示词
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            completeMessages.add(new Message("system", systemPrompt));
        }

        // 从MemoryService获取上下文窗口消息
        if (memoryService != null && conversationId != null) {
            try {
                List<Message> contextMessages = memoryService.getContextMemory(conversationId, maxContextTokens, systemPrompt).block();
                if (contextMessages != null && !contextMessages.isEmpty()) {
                    completeMessages.addAll(contextMessages);
                }
            } catch (Exception e) {
                log.warn("Failed to get context window messages from MemoryService", e);
            }
        }

        return completeMessages;
    }

    /**
     * 获取消息历史的副本（线程安全）
     * 从MemoryService获取完整历史
     */
    public List<Message> getMessageHistory() {
        // 从MemoryService获取完整历史
        if (memoryService != null && conversationId != null) {
            try {
                List<Message> persistedHistory = memoryService.getMemoryHistory(conversationId).block();
                if (persistedHistory != null) {
                    return new ArrayList<>(persistedHistory);
                }
            } catch (Exception e) {
                log.warn("Failed to get memory history from MemoryService", e);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 获取最近的N条消息
     */
    public List<Message> getRecentMessages(int limit) {
        if (memoryService != null && conversationId != null) {
            try {
                List<Message> recentMessages = memoryService.getRecentMemory(conversationId, limit).block();
                if (recentMessages != null) {
                    return recentMessages;
                }
            } catch (Exception e) {
                log.warn("Failed to get recent messages from MemoryService", e);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 获取对话历史（仅用户可见的内容）
     * 从ConversationService获取
     */
    public List<Message> getConversationHistory() {
        if (conversationService != null && conversationId != null) {
            try {
                List<Message> conversationHistory = conversationService.getConversationHistory(conversationId).block();
                if (conversationHistory != null) {
                    return conversationHistory;
                }
            } catch (Exception e) {
                log.warn("Failed to get conversation history from ConversationService", e);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 检查是否达到最大轮次
     */
    public boolean isMaxRoundsReached() {
        return currentRound.get() >= maxRounds;
    }

    /**
     * 增加轮次计数
     */
    public int incrementRound() {
        return currentRound.incrementAndGet();
    }

    /**
     * 获取当前轮次
     */
    public int getCurrentRound() {
        return currentRound.get();
    }

    /**
     * 重置轮次计数
     */
    public void resetRound() {
        currentRound.set(0);
    }

    /**
     * 检查上下文是否有效
     */
    public boolean isValid() {
        return userId != null && conversationId != null &&
               !userId.trim().isEmpty() && !conversationId.trim().isEmpty();
    }


    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        if (metadata != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        if (metadata != null && metadata.containsKey(key)) {
            Object value = metadata.get(key);
            if (type.isInstance(value)) {
                return (T) value;
            }
        }
        return null;
    }

    /**
     * 获取所有元数据
     */
    public Map<String, Object> getAllMetadata() {
        return new ConcurrentHashMap<>(metadata);
    }
}
