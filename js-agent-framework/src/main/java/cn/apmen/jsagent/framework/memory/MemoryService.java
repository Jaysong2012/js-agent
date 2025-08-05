package cn.apmen.jsagent.framework.memory;

import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Agent记忆服务接口
 * 负责管理Agent的短期和长期记忆
 */
public interface MemoryService {

    /**
     * 添加消息到记忆中
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param message 消息
     * @return 添加结果
     */
    Mono<Void> addMessage(String agentId, String sessionId, Message message);

    /**
     * 批量添加消息
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param messages 消息列表
     * @return 添加结果
     */
    Mono<Void> addMessages(String agentId, String sessionId, List<Message> messages);

    /**
     * 获取短期记忆（最近的消息）
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param limit 消息数量限制
     * @return 最近的消息
     */
    Mono<List<Message>> getShortTermMemory(String agentId, String sessionId, int limit);

    /**
     * 获取长期记忆（完整历史）
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @return 完整消息历史
     */
    Mono<List<Message>> getLongTermMemory(String agentId, String sessionId);

    /**
     * 获取适合上下文窗口的记忆
     * 根据token限制智能选择消息
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param maxTokens 最大token数
     * @param systemPrompt 系统提示词（用于token计算）
     * @return 适合上下文窗口的消息列表
     */
    Mono<List<Message>> getContextMemory(String agentId, String sessionId, int maxTokens, String systemPrompt);

    /**
     * 流式获取记忆
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @return 消息流
     */
    Flux<Message> streamMemory(String agentId, String sessionId);

    /**
     * 清空会话记忆
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @return 清空结果
     */
    Mono<Void> clearSessionMemory(String agentId, String sessionId);

    /**
     * 清空Agent的所有记忆
     * @param agentId Agent ID
     * @return 清空结果
     */
    Mono<Void> clearAgentMemory(String agentId);

    /**
     * 获取记忆统计信息
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @return 统计信息
     */
    Mono<MemoryStats> getMemoryStats(String agentId, String sessionId);

    /**
     * 压缩记忆
     * 将旧的消息进行摘要压缩，节省存储空间
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param keepRecentCount 保留最近消息数量
     * @return 压缩结果
     */
    Mono<Void> compressMemory(String agentId, String sessionId, int keepRecentCount);

    /**
     * 检查会话记忆是否存在
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @return 是否存在
     */
    Mono<Boolean> hasSessionMemory(String agentId, String sessionId);

    /**
     * 设置记忆元数据
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param metadata 元数据
     * @return 设置结果
     */
    Mono<Void> setMemoryMetadata(String agentId, String sessionId, MemoryMetadata metadata);

    /**
     * 获取记忆元数据
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @return 元数据
     */
    Mono<MemoryMetadata> getMemoryMetadata(String agentId, String sessionId);

    /**
     * 搜索记忆中的消息
     * @param agentId Agent ID
     * @param sessionId 会话ID
     * @param query 搜索关键词
     * @param limit 结果数量限制
     * @return 匹配的消息
     */
    Mono<List<Message>> searchMemory(String agentId, String sessionId, String query, int limit);
}

