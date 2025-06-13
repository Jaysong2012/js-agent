package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.execution.ExecutionContext;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主要承载Agent执行上下文
 * 现在集成ConversationService进行对话历史管理，并分离执行相关职责到ExecutionContext
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class RunnerContext {


    private ConversationService conversationService;

    private String userId;

    private String conversationId;

    /**
     * 执行上下文 - 管理执行相关的状态和元数据
     */
    private ExecutionContext executionContext;

    /**
     * 本地消息历史记录 - 用于当前会话的临时存储
     * 实际的持久化通过ConversationService管理
     */
    @Builder.Default
    private List<Message> localMessageHistory = Collections.synchronizedList(new ArrayList<>());

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
     * 添加消息到历史记录
     * RunnerContext负责执行期间的临时缓存，ConversationService负责持久化
     * 这里只添加到本地缓存，持久化由AgentRunner统一管理
     */
    public void addMessage(Message message) {
        if (localMessageHistory == null) {
            localMessageHistory = Collections.synchronizedList(new ArrayList<>());
        }

        // 确保localMessageHistory是可变的集合
        if (!(localMessageHistory instanceof ArrayList) &&
            !(localMessageHistory instanceof CopyOnWriteArrayList) &&
            !localMessageHistory.getClass().getName().contains("SynchronizedList")) {
            // 如果是不可变集合，重新创建为可变集合
            List<Message> newHistory = Collections.synchronizedList(new ArrayList<>(localMessageHistory));
            localMessageHistory = newHistory;
        }

        localMessageHistory.add(message);
        log.debug("Message added to local cache: role={}, content={}", 
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
     * 获取完整的消息列表（包含系统提示词）
     * 优先使用ConversationService的上下文窗口管理
     */
    public List<Message> getCompleteMessageList() {
        List<Message> completeMessages = new ArrayList<>();

        // 添加系统提示词
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            completeMessages.add(new Message("system", systemPrompt));
        }

        // 如果有ConversationService，使用智能上下文窗口
        if (conversationService != null && conversationId != null) {
            try {
                List<Message> contextMessages = conversationService.getContextWindowMessages(
                    conversationId, maxContextTokens, systemPrompt).block();
                if (contextMessages != null && !contextMessages.isEmpty()) {
                    // 合并持久化的历史消息和本地缓存的消息
                    completeMessages.addAll(contextMessages);
                    
                    // 添加本地缓存中的新消息（避免重复）
                    if (localMessageHistory != null) {
                        synchronized (localMessageHistory) {
                            for (Message localMsg : localMessageHistory) {
                                // 简单的重复检查：如果最后几条消息不包含当前消息，则添加
                                boolean isDuplicate = contextMessages.stream()
                                    .anyMatch(msg -> msg.getRole().equals(localMsg.getRole()) && 
                                             msg.getContent().equals(localMsg.getContent()));
                                if (!isDuplicate) {
                                    completeMessages.add(localMsg);
                                }
                            }
                        }
                    }
                    return completeMessages;
                }
            } catch (Exception e) {
                log.warn("Failed to get context window messages from ConversationService, falling back to local", e);
            }
        }

        // 降级到本地消息历史
        if (localMessageHistory != null) {
            synchronized (localMessageHistory) {
                completeMessages.addAll(new ArrayList<>(localMessageHistory));
            }
        }

        return completeMessages;
    }

    /**
     * 获取消息历史的副本（线程安全）
     * 优先从ConversationService获取完整历史，本地缓存作为补充
     */
    public List<Message> getMessageHistory() {
        // 如果有ConversationService，从持久化存储获取
        if (conversationService != null && conversationId != null) {
            try {
                List<Message> persistedHistory = conversationService.getConversationHistory(conversationId).block();
                if (persistedHistory != null) {
                    // 合并持久化历史和本地缓存
                    List<Message> mergedHistory = new ArrayList<>(persistedHistory);
                    
                    // 添加本地缓存中的新消息
                    if (localMessageHistory != null) {
                        synchronized (localMessageHistory) {
                            for (Message localMsg : localMessageHistory) {
                                // 避免重复添加
                                boolean isDuplicate = persistedHistory.stream()
                                    .anyMatch(msg -> msg.getRole().equals(localMsg.getRole()) && 
                                             msg.getContent().equals(localMsg.getContent()));
                                if (!isDuplicate) {
                                    mergedHistory.add(localMsg);
                                }
                            }
                        }
                    }
                    return mergedHistory;
                }
            } catch (Exception e) {
                log.warn("Failed to get conversation history from ConversationService, falling back to local", e);
            }
        }
        
        // 降级到本地历史
        if (localMessageHistory == null) {
            return new ArrayList<>();
        }
        synchronized (localMessageHistory) {
            return new ArrayList<>(localMessageHistory);
        }
    }

    /**
     * 获取最近的N条消息
     */
    public List<Message> getRecentMessages(int limit) {
        if (conversationService != null && conversationId != null) {
            try {
                List<Message> recentMessages = conversationService.getRecentMessages(conversationId, limit).block();
                if (recentMessages != null) {
                    return recentMessages;
                }
            } catch (Exception e) {
                log.warn("Failed to get recent messages from ConversationService, falling back to local", e);
            }
        }
        // 降级到本地历史
        if (localMessageHistory == null || localMessageHistory.isEmpty()) {
            return new ArrayList<>();
        }
        synchronized (localMessageHistory) {
            int size = localMessageHistory.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(localMessageHistory.subList(fromIndex, size));
        }
    }

    /**
     * 检查是否达到最大轮次
     */
    public boolean isMaxRoundsReached() {
        // 优先使用ExecutionContext的轮次管理
        if (executionContext != null) {
            return executionContext.isMaxRoundsReached();
        }
        return currentRound.get() >= maxRounds;
    }

    /**
     * 增加轮次计数
     */
    public int incrementRound() {
        // 同时更新ExecutionContext和本地计数器
        if (executionContext != null) {
            executionContext.incrementRound();
        }
        return currentRound.incrementAndGet();
    }

    /**
     * 获取当前轮次
     */
    public int getCurrentRound() {
        // 优先使用ExecutionContext的轮次
        if (executionContext != null) {
            return executionContext.getCurrentRound();
        }
        return currentRound.get();
    }

    /**
     * 重置轮次计数
     */
    public void resetRound() {
        currentRound.set(0);
        if (executionContext != null) {
            executionContext.setCurrentRound(0);
        }
    }

    /**
     * 检查上下文是否有效
     */
    public boolean isValid() {
        return userId != null && conversationId != null &&
               !userId.trim().isEmpty() && !conversationId.trim().isEmpty();
    }

    /**
     * 同步本地历史到ConversationService
     * 这个方法主要用于批量同步，正常情况下消息应该实时持久化
     */
    public void syncToConversationService() {
        if (conversationService != null && conversationId != null && localMessageHistory != null) {
            synchronized (localMessageHistory) {
                if (!localMessageHistory.isEmpty()) {
                    conversationService.addMessages(conversationId, new ArrayList<>(localMessageHistory))
                        .doOnSuccess(v -> {
                            log.debug("Synced {} messages to ConversationService", localMessageHistory.size());
                            // 同步成功后清空本地缓存（可选）
                            // localMessageHistory.clear();
                        })
                        .doOnError(error -> log.warn("Failed to sync messages to ConversationService", error))
                        .subscribe();
                }
            }
        }
    }

    /**
     * 清空本地消息缓存
     * 注意：这不会影响ConversationService中的持久化数据
     */
    public void clearLocalMessageCache() {
        if (localMessageHistory != null) {
            synchronized (localMessageHistory) {
                localMessageHistory.clear();
                log.debug("Local message cache cleared for conversation: {}", conversationId);
            }
        }
    }

    /**
     * 获取本地缓存的消息数量
     */
    public int getLocalMessageCount() {
        if (localMessageHistory == null) {
            return 0;
        }
        synchronized (localMessageHistory) {
            return localMessageHistory.size();
        }
    }

    // 委托给ExecutionContext的方法

    /**
     * 获取执行ID
     */
    public String getExecutionId() {
        return executionContext != null ? executionContext.getExecutionId() : null;
    }

    /**
     * 获取Agent ID
     */
    public String getAgentId() {
        return executionContext != null ? executionContext.getAgentId() : null;
    }

    /**
     * 设置执行元数据
     */
    public void setExecutionMetadata(String key, Object value) {
        if (executionContext != null) {
            executionContext.setMetadata(key, value);
        }
    }

    /**
     * 获取执行元数据
     */
    public <T> T getExecutionMetadata(String key, Class<T> type) {
        return executionContext != null ? executionContext.getMetadata(key, type) : null;
    }

    /**
     * 获取执行指标
     */
    public ExecutionContext.ExecutionMetrics getExecutionMetrics() {
        return executionContext != null ? executionContext.getMetrics() : null;
    }
}
