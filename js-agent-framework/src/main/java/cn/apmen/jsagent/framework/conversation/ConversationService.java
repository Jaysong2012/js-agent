package cn.apmen.jsagent.framework.conversation;

import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 对话服务接口
 * 负责管理对话历史记录、上下文窗口和持久化
 */
public interface ConversationService {
    
    /**
     * 添加消息到会话历史
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
     * 获取会话的完整历史记录
     * @param conversationId 会话ID
     * @return 消息历史
     */
    Mono<List<Message>> getConversationHistory(String conversationId);
    
    /**
     * 获取会话的最近N条消息
     * @param conversationId 会话ID
     * @param limit 消息数量限制
     * @return 最近的消息
     */
    Mono<List<Message>> getRecentMessages(String conversationId, int limit);
    
    /**
     * 获取适合当前上下文窗口的消息
     * 根据token限制智能截取消息历史
     * @param conversationId 会话ID
     * @param maxTokens 最大token数
     * @param systemPrompt 系统提示词
     * @return 适合上下文窗口的消息列表
     */
    Mono<List<Message>> getContextWindowMessages(String conversationId, int maxTokens, String systemPrompt);
    
    /**
     * 流式获取消息历史
     * @param conversationId 会话ID
     * @return 消息流
     */
    Flux<Message> streamConversationHistory(String conversationId);
    
    /**
     * 清空会话历史
     * @param conversationId 会话ID
     * @return 清空结果
     */
    Mono<Void> clearConversation(String conversationId);
    
    /**
     * 删除会话
     * @param conversationId 会话ID
     * @return 删除结果
     */
    Mono<Void> deleteConversation(String conversationId);
    
    /**
     * 获取会话统计信息
     * @param conversationId 会话ID
     * @return 统计信息
     */
    Mono<ConversationStats> getConversationStats(String conversationId);
    
    /**
     * 获取用户的所有会话ID
     * @param userId 用户ID
     * @return 会话ID列表
     */
    Mono<List<String>> getUserConversations(String userId);
    
    /**
     * 压缩历史记录
     * 将旧的消息进行摘要压缩，节省存储空间
     * @param conversationId 会话ID
     * @param keepRecentCount 保留最近消息数量
     * @return 压缩结果
     */
    Mono<Void> compressHistory(String conversationId, int keepRecentCount);
    
    /**
     * 检查会话是否存在
     * @param conversationId 会话ID
     * @return 是否存在
     */
    Mono<Boolean> conversationExists(String conversationId);
    
    /**
     * 设置会话的元数据
     * @param conversationId 会话ID
     * @param metadata 元数据
     * @return 设置结果
     */
    Mono<Void> setConversationMetadata(String conversationId, ConversationMetadata metadata);
    
    /**
     * 获取会话的元数据
     * @param conversationId 会话ID
     * @return 元数据
     */
    Mono<ConversationMetadata> getConversationMetadata(String conversationId);
}