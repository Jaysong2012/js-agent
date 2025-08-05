package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent请求对象
 * 封装了Agent执行所需的所有信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {

    /**
     * 用户消息内容
     */
    private String userMessage;

    /**
     * 工具调用列表（用于Act阶段）
     */
    private List<ToolCall> toolCalls;

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 请求ID（用于追踪）
     */
    private String requestId;

    /**
     * 是否为流式请求
     */
    @Builder.Default
    private boolean streaming = false;

    /**
     * 最大轮次限制
     */
    private Integer maxRounds;

    /**
     * 上下文信息
     */
    private Object context;

    /**
     * 创建简单的文本请求
     */
    public static AgentRequest text(String message) {
        return AgentRequest.builder()
            .userMessage(message)
            .build();
    }

    /**
     * 创建工具调用请求
     */
    public static AgentRequest toolCall(List<ToolCall> toolCalls) {
        return AgentRequest.builder()
            .toolCalls(toolCalls)
            .build();
    }

    /**
     * 创建带会话信息的请求
     */
    public static AgentRequest withConversation(String message, String conversationId, String userId) {
        return AgentRequest.builder()
            .userMessage(message)
            .conversationId(conversationId)
            .userId(userId)
            .build();
    }
}
