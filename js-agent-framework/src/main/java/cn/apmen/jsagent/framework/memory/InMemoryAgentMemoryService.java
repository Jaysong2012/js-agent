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
 * 基于内存的记忆服务实现
 * 适用于开发和测试环境
 */
@Slf4j
public class InMemoryAgentMemoryService implements AgentMemoryService {

    // 存储结构：agentId -> sessionId -> messages
    private final Map<String, Map<String, List<Message>>> memoryStore = new ConcurrentHashMap<>();

    // 元数据存储：agentId -> sessionId -> metadata
    private final Map<String, Map<String, MemoryMetadata>> metadataStore = new ConcurrentHashMap<>();

    // 默认配置
    private static final int DEFAULT_SHORT_TERM_LIMIT = 20;
    private static final int DEFAULT_CONTEXT_TOKEN_LIMIT = 4000;
    private static final int ESTIMATED_TOKENS_PER_MESSAGE = 50; // 粗略估算

    @Override
    public Mono<Void> addMessage(String agentId, String sessionId, Message message) {
        return Mono.fromRunnable(() -> {
            memoryStore.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>())
                      .computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                      .add(message);

            // 更新元数据
            updateMetadata(agentId, sessionId);

            log.debug("Added message to memory: agent={}, session={}, role={}",
                     agentId, sessionId, message.getRole());
        });
    }

    @Override
    public Mono<Void> addMessages(String agentId, String sessionId, List<Message> messages) {
        return Mono.fromRunnable(() -> {
            if (messages == null || messages.isEmpty()) {
                return;
            }

            memoryStore.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>())
                      .computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                      .addAll(messages);

            // 更新元数据
            updateMetadata(agentId, sessionId);

            log.debug("Added {} messages to memory: agent={}, session={}",
                     messages.size(), agentId, sessionId);
        });
    }

    @Override
    public Mono<List<Message>> getShortTermMemory(String agentId, String sessionId, int limit) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForSession(agentId, sessionId);
            if (messages.isEmpty()) {
                return new ArrayList<>();
            }

            int actualLimit = Math.min(limit, messages.size());
            int startIndex = Math.max(0, messages.size() - actualLimit);

            return new ArrayList<>(messages.subList(startIndex, messages.size()));
        });
    }

    @Override
    public Mono<List<Message>> getLongTermMemory(String agentId, String sessionId) {
        return Mono.fromCallable(() -> new ArrayList<>(getMessagesForSession(agentId, sessionId)));
    }

    @Override
    public Mono<List<Message>> getContextMemory(String agentId, String sessionId, int maxTokens, String systemPrompt) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForSession(agentId, sessionId);
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
    public Flux<Message> streamMemory(String agentId, String sessionId) {
        return Flux.fromIterable(getMessagesForSession(agentId, sessionId));
    }

    @Override
    public Mono<Void> clearSessionMemory(String agentId, String sessionId) {
        return Mono.fromRunnable(() -> {
            Map<String, List<Message>> agentMemory = memoryStore.get(agentId);
            if (agentMemory != null) {
                agentMemory.remove(sessionId);
            }

            Map<String, MemoryMetadata> agentMetadata = metadataStore.get(agentId);
            if (agentMetadata != null) {
                agentMetadata.remove(sessionId);
            }

            log.debug("Cleared session memory: agent={}, session={}", agentId, sessionId);
        });
    }

    @Override
    public Mono<Void> clearAgentMemory(String agentId) {
        return Mono.fromRunnable(() -> {
            memoryStore.remove(agentId);
            metadataStore.remove(agentId);
            log.debug("Cleared all memory for agent: {}", agentId);
        });
    }

    @Override
    public Mono<MemoryStats> getMemoryStats(String agentId, String sessionId) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForSession(agentId, sessionId);
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
    public Mono<Void> compressMemory(String agentId, String sessionId, int keepRecentCount) {
        return Mono.fromRunnable(() -> {
            List<Message> messages = getMessagesForSession(agentId, sessionId);
            if (messages.size() <= keepRecentCount) {
                return;
            }

            // 简单压缩：只保留最近的消息
            List<Message> recentMessages = messages.subList(
                messages.size() - keepRecentCount, messages.size());

            // 创建压缩摘要消息
            Message compressionSummary = new Message("system",
                String.format("[COMPRESSED] Removed %d older messages, kept recent %d messages",
                    messages.size() - keepRecentCount, keepRecentCount));

            // 更新存储
            List<Message> newMessages = Collections.synchronizedList(new ArrayList<>());
            newMessages.add(compressionSummary);
            newMessages.addAll(recentMessages);

            memoryStore.get(agentId).put(sessionId, newMessages);

            log.debug("Compressed memory: agent={}, session={}, kept={}, removed={}",
                     agentId, sessionId, keepRecentCount, messages.size() - keepRecentCount);
        });
    }

    @Override
    public Mono<Boolean> hasSessionMemory(String agentId, String sessionId) {
        return Mono.fromCallable(() -> {
            Map<String, List<Message>> agentMemory = memoryStore.get(agentId);
            return agentMemory != null && agentMemory.containsKey(sessionId)
                   && !agentMemory.get(sessionId).isEmpty();
        });
    }

    @Override
    public Mono<Void> setMemoryMetadata(String agentId, String sessionId, MemoryMetadata metadata) {
        return Mono.fromRunnable(() -> {
            metadataStore.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>())
                        .put(sessionId, metadata);
            log.debug("Set memory metadata: agent={}, session={}", agentId, sessionId);
        });
    }

    @Override
    public Mono<MemoryMetadata> getMemoryMetadata(String agentId, String sessionId) {
        return Mono.fromCallable(() -> {
            Map<String, MemoryMetadata> agentMetadata = metadataStore.get(agentId);
            if (agentMetadata != null) {
                return agentMetadata.get(sessionId);
            }
            return null;
        });
    }

    @Override
    public Mono<List<Message>> searchMemory(String agentId, String sessionId, String query, int limit) {
        return Mono.fromCallable(() -> {
            List<Message> messages = getMessagesForSession(agentId, sessionId);
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
    private List<Message> getMessagesForSession(String agentId, String sessionId) {
        Map<String, List<Message>> agentMemory = memoryStore.get(agentId);
        if (agentMemory == null) {
            return new ArrayList<>();
        }

        List<Message> messages = agentMemory.get(sessionId);
        return messages != null ? messages : new ArrayList<>();
    }

    /**
     * 更新元数据
     */
    private void updateMetadata(String agentId, String sessionId) {
        MemoryMetadata metadata = metadataStore.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>())
                                              .computeIfAbsent(sessionId, k -> MemoryMetadata.builder()
                                                  .createdAt(LocalDateTime.now())
                                                  .build());

        metadata.setUpdatedAt(LocalDateTime.now());
    }
}

