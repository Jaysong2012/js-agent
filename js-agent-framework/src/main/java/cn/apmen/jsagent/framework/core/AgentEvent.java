package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 事件内容
     */
    private String content;

    /**
     * 工具调用列表（当type为TOOL_CALL时）
     */
    private List<ToolCall> toolCalls;

    /**
     * 是否为最终事件
     */
    private boolean isFinal;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 事件时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 事件类型枚举
     */
    public enum EventType {
        TEXT_RESPONSE,  // 文本响应
        TOOL_CALL,      // 工具调用
        THINKING,       // 思考中
        ERROR          // 错误
    }

    // 静态工厂方法
    public static AgentEvent textResponse(String content, boolean isFinal) {
        return AgentEvent.builder()
                .type(EventType.TEXT_RESPONSE)
                .content(content)
                .isFinal(isFinal)
                .build();
    }

    public static AgentEvent toolCall(List<ToolCall> toolCalls) {
        return AgentEvent.builder()
                .type(EventType.TOOL_CALL)
                .toolCalls(toolCalls)
                .isFinal(false)
                .build();
    }

    public static AgentEvent thinking(String content) {
        return AgentEvent.builder()
                .type(EventType.THINKING)
                .content(content)
                .isFinal(false)
                .build();
    }

    public static AgentEvent error(String error) {
        return AgentEvent.builder()
                .type(EventType.ERROR)
                .error(error)
                .isFinal(true)
                .build();
    }
}
