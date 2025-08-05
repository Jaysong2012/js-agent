package cn.apmen.jsagent.framework.memory;

import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 记忆服务接口（基于conversationId）
 * 负责记录Agent运行中的所有事件和消息（assistant/tool/system等）
 * 主要用于CoreAgent等需要完整记录Agent执行过程的场景
 */
public interface MemoryService {

    /**
     * 添加消息到记忆中
     * @param conversationId 会话ID
     * @param message 消息
     * @return 添加结果
     */
    Mono<Void> addMessage(String conversationId, Message message);

    /**
     * 批量添加消息
     * @param conversationId 会话ID
     * @param messages 消息列表
     * @return 添加结果
     */
    Mono<Void> addMessages(String conversationId, List<Message> messages);

    /**
     * 获取完整的记忆历史
     * @param conversationId 会话ID
     * @return 消息历史
     */
    Mono<List<Message>> getMemoryHistory(String conversationId);

    /**
     * 获取最近N条记忆
     * @param conversationId 会话ID
     * @param limit 消息数量限制
     * @return 最近的消息
     */
    Mono<List<Message>> getRecentMemory(String conversationId, int limit);

    /**
     * 获取适合当前上下文窗口的记忆
     * 根据token限制智能截取消息历史
     * @param conversationId 会话ID
     * @param maxTokens 最大token数
     * @param systemPrompt 系统提示词
     * @return 适合上下文窗口的消息列表
     */
    Mono<List<Message>> getContextMemory(String conversationId, int maxTokens, String systemPrompt);

    /**
     * 流式获取记忆历史
     * @param conversationId 会话ID
     * @return 消息流
     */
    Flux<Message> streamMemoryHistory(String conversationId);

    /**
     * 清空会话记忆
     * @param conversationId 会话ID
     * @return 清空结果
     */
    Mono<Void> clearMemory(String conversationId);

    /**
     * 获取记忆统计信息
     * @param conversationId 会话ID
     * @return 统计信息
     */
    Mono<MemoryStats> getMemoryStats(String conversationId);

    /**
     * 压缩记忆历史
     * 将旧的消息进行摘要压缩，节省存储空间
     * @param conversationId 会话ID
     * @param keepRecentCount 保留最近消息数量
     * @return 压缩结果
     */
    Mono<Void> compressMemory(String conversationId, int keepRecentCount);

    /**
     * 检查会话记忆是否存在
     * @param conversationId 会话ID
     * @return 是否存在
     */
    Mono<Boolean> memoryExists(String conversationId);

    /**
     * 设置记忆的元数据
     * @param conversationId 会话ID
     * @param metadata 元数据
     * @return 设置结果
     */
    Mono<Void> setMemoryMetadata(String conversationId, MemoryMetadata metadata);

    /**
     * 获取记忆的元数据
     * @param conversationId 会话ID
     * @return 元数据
     */
    Mono<MemoryMetadata> getMemoryMetadata(String conversationId);

    /**
     * 搜索记忆中的消息
     * @param conversationId 会话ID
     * @param query 搜索关键词
     * @param limit 结果数量限制
     * @return 匹配的消息
     */
    Mono<List<Message>> searchMemory(String conversationId, String query, int limit);
}

