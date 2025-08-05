package cn.apmen.jsagent.framework.memory;

import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内存的记忆服务实现（基于conversationId）
 * 适用于开发和测试环境
 */
@Slf4j
public class InMemoryMemoryService implements MemoryService {

    // 存储结构：conversationId -> messages
    private final Map<String, List<Message>> memoryStore = new ConcurrentHashMap<>();

    // 元数据存储：conversationId -> metadata
    private final Map<String, MemoryMetadata> metadataStore = new ConcurrentHashMap<>();

    // 默认配置
    private static final int DEFAULT_RECENT_LIMIT = 20;
    private static final int DEFAULT_CONTEXT_TOKEN_LIMIT = 4000;
    private static final int ESTIMATED_TOKENS_PER_MESSAGE = 50; // 粗略估算

    @Override
    public Mono<Void> addMessage(String conversationId, Message message) {
        return Mono.fromRunnable(() -> {
            memoryStore.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()))
                      .add(message);

            // 更新元数据
            updateMetadata(conversationId);

            log.debug("Added message to memory: conversation={}, role={}",
                     conversationId, message.getRole());
        });
    }

    @Override
    public Mono<Void> addMessages(String conversationId, List<Message> messages) {
        return Mono.fromRunnable(() -> {
            if (messages == null || messages.isEmpty()) {
                return;
            }

            memoryStore.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()))
                      .addAll(messages);

            // 更新元数据
            updateMetadata(conversationId);

            log.debug("Added {} messages to memory: conversation={}",
                     messages.size(), conversationId);
        });
    }

    @Override
    public Mono<List<Message>> getMemoryHistory(String conversationId) {
        return Mono.fromCallable(() -> new ArrayList<>(getMessagesForConversation(conversationId)));
    }

    @Override
    public Mono<List<Message>> getRecentMemory(String conversationId, int limit) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForConversation(conversationId);
            if (messages.isEmpty()) {
                return new ArrayList<>();
            }

            int actualLimit = Math.min(limit, messages.size());
            int startIndex = Math.max(0, messages.size() - actualLimit);

            return new ArrayList<>(messages.subList(startIndex, messages.size()));
        });
    }

    @Override
    public Mono<List<Message>> getContextMemory(String conversationId, int maxTokens, String systemPrompt) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForConversation(conversationId);
            if (messages.isEmpty()) {
                return new ArrayList<>();
            }

            // 简单的token估算策略
            int systemPromptTokens = systemPrompt != null ? systemPrompt.length() / 4 : 0;
            int availableTokens = maxTokens - systemPromptTokens;
            int maxMessages = Math.max(1, availableTokens / ESTIMATED_TOKENS_PER_MESSAGE);

            if (messages.size() <= maxMessages) {
                return new ArrayList<>(messages);
            }

            // 保留最近的消息
            int startIndex = messages.size() - maxMessages;
            return new ArrayList<>(messages.subList(startIndex, messages.size()));
        });
    }

    @Override
    public Flux<Message> streamMemoryHistory(String conversationId) {
        return Flux.fromIterable(getMessagesForConversation(conversationId));
    }

    @Override
    public Mono<Void> clearMemory(String conversationId) {
        return Mono.fromRunnable(() -> {
            memoryStore.remove(conversationId);
            metadataStore.remove(conversationId);
            log.debug("Cleared memory for conversation: {}", conversationId);
        });
    }

    @Override
    public Mono<MemoryStats> getMemoryStats(String conversationId) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForConversation(conversationId);
            if (messages.isEmpty()) {
                return MemoryStats.builder()
                    .totalMessages(0)
                    .build();
            }

            // 统计各类型消息数量
            Map<String, Long> roleCount = messages.stream()
                .collect(Collectors.groupingBy(Message::getRole, Collectors.counting()));

            LocalDateTime firstTime = LocalDateTime.now();
            LocalDateTime lastTime = LocalDateTime.now();

            return MemoryStats.builder()
                .totalMessages(messages.size())
                .userMessages(roleCount.getOrDefault("user", 0L).intValue())
                .assistantMessages(roleCount.getOrDefault("assistant", 0L).intValue())
                .toolMessages(roleCount.getOrDefault("tool", 0L).intValue())
                .systemMessages(roleCount.getOrDefault("system", 0L).intValue())
                .estimatedTokens(messages.size() * ESTIMATED_TOKENS_PER_MESSAGE)
                .firstMessageTime(firstTime)
                .lastMessageTime(lastTime)
                .sessionDurationMinutes(ChronoUnit.MINUTES.between(firstTime, lastTime))
                .compressed(false)
                .build();
        });
    }

    @Override
    public Mono<Void> compressMemory(String conversationId, int keepRecentCount) {
        return Mono.fromRunnable(() -> {
            List<Message> messages = getMessagesForConversation(conversationId);
            if (messages.size() <= keepRecentCount) {
                return;
            }

            // 简单的压缩策略：只保留最近的消息
            List<Message> recentMessages = messages.subList(
                messages.size() - keepRecentCount, messages.size());

            memoryStore.put(conversationId, Collections.synchronizedList(new ArrayList<>(recentMessages)));

            // 更新元数据
            MemoryMetadata metadata = metadataStore.get(conversationId);
            if (metadata != null) {
                metadata.setUpdatedAt(LocalDateTime.now());
            }

            log.debug("Compressed memory for conversation: {}, kept {} recent messages",
                     conversationId, keepRecentCount);
        });
    }

    @Override
    public Mono<Boolean> memoryExists(String conversationId) {
        return Mono.fromCallable(() -> memoryStore.containsKey(conversationId));
    }

    @Override
    public Mono<Void> setMemoryMetadata(String conversationId, MemoryMetadata metadata) {
        return Mono.fromRunnable(() -> {
            metadataStore.put(conversationId, metadata);
            log.debug("Set memory metadata for conversation: {}", conversationId);
        });
    }

    @Override
    public Mono<MemoryMetadata> getMemoryMetadata(String conversationId) {
        return Mono.fromCallable(() -> metadataStore.get(conversationId));
    }

    @Override
    public Mono<List<Message>> searchMemory(String conversationId, String query, int limit) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForConversation(conversationId);
            if (messages.isEmpty() || query == null || query.trim().isEmpty()) {
                return new ArrayList<>();
            }

            String lowerQuery = query.toLowerCase();
            return messages.stream()
                .filter(message -> message.getContent() != null &&
                                 message.getContent().toLowerCase().contains(lowerQuery))
                .limit(limit)
                .collect(Collectors.toList());
        });
    }

    /**
     * 获取指定会话的消息列表
     */
    private List<Message> getMessagesForConversation(String conversationId) {
        List<Message> messages = memoryStore.get(conversationId);
        return messages != null ? messages : new ArrayList<>();
    }

    /**
     * 更新元数据
     */
    private void updateMetadata(String conversationId) {
        MemoryMetadata metadata = metadataStore.computeIfAbsent(conversationId, k -> MemoryMetadata.builder()
                                              .createdAt(LocalDateTime.now())
                                              .build());

        metadata.setUpdatedAt(LocalDateTime.now());
    }
}

