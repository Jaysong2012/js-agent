package cn.apmen.jsagent.framework.conversation.impl;

import cn.apmen.jsagent.framework.conversation.ConversationMetadata;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.conversation.ConversationStats;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内存的ConversationService实现
 * 适用于开发和测试环境，生产环境建议使用持久化实现
 */
@Slf4j
@Service
public class InMemoryConversationService implements ConversationService {
    
    // 会话消息存储 conversationId -> List<Message>
    private final Map<String, List<Message>> conversationMessages = new ConcurrentHashMap<>();
    
    // 会话元数据存储 conversationId -> ConversationMetadata
    private final Map<String, ConversationMetadata> conversationMetadata = new ConcurrentHashMap<>();
    
    // 用户会话映射 userId -> Set<conversationId>
    private final Map<String, Set<String>> userConversations = new ConcurrentHashMap<>();
    
    // Token估算器（简单实现）
    private final TokenEstimator tokenEstimator = new TokenEstimator();
    
    @Override
    public Mono<Void> addMessage(String conversationId, Message message) {
        return Mono.fromRunnable(() -> {
            conversationMessages.computeIfAbsent(conversationId, k -> 
                Collections.synchronizedList(new ArrayList<>())).add(message);
            
            // 更新元数据的最后活跃时间
            updateLastActiveTime(conversationId);
            
            log.debug("Added message to conversation {}: {}", conversationId, message.getRole());
        });
    }
    
    @Override
    public Mono<Void> addMessages(String conversationId, List<Message> messages) {
        return Mono.fromRunnable(() -> {
            List<Message> conversationHistory = conversationMessages.computeIfAbsent(conversationId, k -> 
                Collections.synchronizedList(new ArrayList<>()));
            conversationHistory.addAll(messages);
            
            updateLastActiveTime(conversationId);
            
            log.debug("Added {} messages to conversation {}", messages.size(), conversationId);
        });
    }
    
    @Override
    public Mono<List<Message>> getConversationHistory(String conversationId) {
        return Mono.fromCallable(() -> {
            List<Message> messages = conversationMessages.get(conversationId);
            return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        });
    }
    
    @Override
    public Mono<List<Message>> getRecentMessages(String conversationId, int limit) {
        return Mono.fromCallable(() -> {
            List<Message> messages = conversationMessages.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return new ArrayList<>();
            }
            
            int size = messages.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(messages.subList(fromIndex, size));
        });
    }
    
    @Override
    public Mono<List<Message>> getContextWindowMessages(String conversationId, int maxTokens, String systemPrompt) {
        return Mono.fromCallable(() -> {
            List<Message> messages = conversationMessages.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 系统提示词的token数
            int systemPromptTokens = systemPrompt != null ? tokenEstimator.estimateTokens(systemPrompt) : 0;
            int availableTokens = maxTokens - systemPromptTokens - 100; // 预留100个token
            
            List<Message> result = new ArrayList<>();
            int currentTokens = 0;
            
            // 从最新消息开始，向前添加消息直到达到token限制
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                int messageTokens = tokenEstimator.estimateMessageTokens(message);
                
                if (currentTokens + messageTokens > availableTokens) {
                    break;
                }
                
                result.add(0, message); // 添加到开头保持顺序
                currentTokens += messageTokens;
            }
            
            log.debug("Selected {} messages ({} tokens) for context window from conversation {}", 
                result.size(), currentTokens, conversationId);
            
            return result;
        });
    }
    
    @Override
    public Flux<Message> streamConversationHistory(String conversationId) {
        return Flux.fromIterable(conversationMessages.getOrDefault(conversationId, Collections.emptyList()));
    }
    
    @Override
    public Mono<Void> clearConversation(String conversationId) {
        return Mono.fromRunnable(() -> {
            conversationMessages.remove(conversationId);
            updateLastActiveTime(conversationId);
            log.info("Cleared conversation history: {}", conversationId);
        });
    }
    
    @Override
    public Mono<Void> deleteConversation(String conversationId) {
        return Mono.fromRunnable(() -> {
            conversationMessages.remove(conversationId);
            ConversationMetadata metadata = conversationMetadata.remove(conversationId);
            
            // 从用户会话映射中移除
            if (metadata != null && metadata.getUserId() != null) {
                Set<String> userConvs = userConversations.get(metadata.getUserId());
                if (userConvs != null) {
                    userConvs.remove(conversationId);
                }
            }
            
            log.info("Deleted conversation: {}", conversationId);
        });
    }
    
    @Override
    public Mono<ConversationStats> getConversationStats(String conversationId) {
        return Mono.fromCallable(() -> {
            List<Message> messages = conversationMessages.get(conversationId);
            ConversationMetadata metadata = conversationMetadata.get(conversationId);
            
            if (messages == null) {
                return ConversationStats.builder()
                    .conversationId(conversationId)
                    .totalMessages(0)
                    .build();
            }
            
            // 统计各类型消息数量
            Map<String, Long> roleCounts = messages.stream()
                .collect(Collectors.groupingBy(Message::getRole, Collectors.counting()));
            
            int totalTokens = messages.stream()
                .mapToInt(tokenEstimator::estimateMessageTokens)
                .sum();
            
            ConversationStats.ConversationStatsBuilder builder = ConversationStats.builder()
                .conversationId(conversationId)
                .totalMessages(messages.size())
                .userMessages(roleCounts.getOrDefault("user", 0L).intValue())
                .assistantMessages(roleCounts.getOrDefault("assistant", 0L).intValue())
                .systemMessages(roleCounts.getOrDefault("system", 0L).intValue())
                .toolMessages(roleCounts.getOrDefault("tool", 0L).intValue())
                .totalTokens(totalTokens);
            
            if (metadata != null) {
                builder.createdAt(metadata.getCreatedAt())
                       .lastUpdatedAt(metadata.getLastActiveAt());
                
                if (metadata.getCreatedAt() != null && metadata.getLastActiveAt() != null) {
                    long duration = java.time.Duration.between(metadata.getCreatedAt(), metadata.getLastActiveAt()).toMinutes();
                    builder.durationMinutes(duration);
                }
            }
            
            return builder.build();
        });
    }
    
    @Override
    public Mono<List<String>> getUserConversations(String userId) {
        return Mono.fromCallable(() -> {
            Set<String> conversations = userConversations.get(userId);
            return conversations != null ? new ArrayList<>(conversations) : new ArrayList<>();
        });
    }
    
    @Override
    public Mono<Void> compressHistory(String conversationId, int keepRecentCount) {
        return Mono.fromRunnable(() -> {
            List<Message> messages = conversationMessages.get(conversationId);
            if (messages == null || messages.size() <= keepRecentCount) {
                return;
            }
            
            // 保留最近的消息，压缩旧消息为摘要
            List<Message> recentMessages = messages.subList(messages.size() - keepRecentCount, messages.size());
            List<Message> oldMessages = messages.subList(0, messages.size() - keepRecentCount);
            
            // 创建压缩摘要
            String summary = createConversationSummary(oldMessages);
            Message summaryMessage = new Message("system", "Previous conversation summary: " + summary);
            
            // 更新消息列表
            List<Message> compressedMessages = Collections.synchronizedList(new ArrayList<>());
            compressedMessages.add(summaryMessage);
            compressedMessages.addAll(recentMessages);
            
            conversationMessages.put(conversationId, compressedMessages);
            
            log.info("Compressed conversation {}: {} messages -> {} messages", 
                conversationId, messages.size(), compressedMessages.size());
        });
    }
    
    @Override
    public Mono<Boolean> conversationExists(String conversationId) {
        return Mono.fromCallable(() -> conversationMessages.containsKey(conversationId));
    }
    
    @Override
    public Mono<Void> setConversationMetadata(String conversationId, ConversationMetadata metadata) {
        return Mono.fromRunnable(() -> {
            conversationMetadata.put(conversationId, metadata);
            
            // 更新用户会话映射
            if (metadata.getUserId() != null) {
                userConversations.computeIfAbsent(metadata.getUserId(), k -> 
                    Collections.synchronizedSet(new HashSet<>())).add(conversationId);
            }
            
            log.debug("Set metadata for conversation {}", conversationId);
        });
    }
    
    @Override
    public Mono<ConversationMetadata> getConversationMetadata(String conversationId) {
        return Mono.fromCallable(() -> conversationMetadata.get(conversationId));
    }
    
    /**
     * 更新会话的最后活跃时间
     */
    private void updateLastActiveTime(String conversationId) {
        conversationMetadata.computeIfPresent(conversationId, (id, metadata) -> {
            metadata.setLastActiveAt(LocalDateTime.now());
            return metadata;
        });
    }
    
    /**
     * 创建对话摘要
     */
    private String createConversationSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return "No previous messages.";
        }
        
        // 简单的摘要实现，实际可以使用LLM生成更好的摘要
        StringBuilder summary = new StringBuilder();
        summary.append("Conversation included ").append(messages.size()).append(" messages. ");
        
        Map<String, Long> roleCounts = messages.stream()
            .collect(Collectors.groupingBy(Message::getRole, Collectors.counting()));
        
        roleCounts.forEach((role, count) -> 
            summary.append(count).append(" ").append(role).append(" messages, "));
        
        return summary.toString();
    }
    
    /**
     * Token估算器
     */
    private static class TokenEstimator {
        
        /**
         * 估算文本的token数量（简单实现）
         */
        public int estimateTokens(String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            // 简单估算：平均4个字符 = 1个token
            return (int) Math.ceil(text.length() / 4.0);
        }
        
        /**
         * 估算消息的token数量
         */
        public int estimateMessageTokens(Message message) {
            int tokens = 0;
            
            if (message.getContent() != null) {
                tokens += estimateTokens(message.getContent());
            }
            
            if (message.getRole() != null) {
                tokens += estimateTokens(message.getRole());
            }
            
            // 工具调用的额外token
            if (message.getToolCalls() != null) {
                tokens += message.getToolCalls().size() * 10; // 每个工具调用估算10个token
            }
            
            return tokens + 4; // 消息结构的额外token
        }
    }
}