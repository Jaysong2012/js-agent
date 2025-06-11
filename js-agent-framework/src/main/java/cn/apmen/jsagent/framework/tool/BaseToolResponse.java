package cn.apmen.jsagent.framework.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 工具响应基类
 * 所有工具响应都应该继承此类
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseToolResponse {
    /**
     * 工具调用ID
     */
    private String toolCallId;
    /**
     * 执行是否成功
     */
    private boolean success;
    /**
     * 响应内容
     */
    private String content;
    /**
     * 错误信息（如果执行失败）
     */
    private String error;
    /**
     * 响应时间戳
     */
    private LocalDateTime timestamp;
    /**
     * 是否直接输出给用户（跳过后续Agent处理）
     */
    private boolean directOutput;
    /**
     * 响应类型
     */
    private ResponseType responseType;

    /**
     * 响应类型枚举
     */
    public enum ResponseType {
        TEXT,           // 文本响应
        STREAM,         // 流式响应
        AGENT_CALL,     // Agent调用响应
        ERROR           // 错误响应
    }

    /**
     * 获取响应类型（子类需要实现）
     */
    public abstract ResponseType getResponseType();

    /**
     * 是否为流式响应
     */
    public boolean isStreamResponse() {
        return getResponseType() == ResponseType.STREAM;
    }

    /**
     * 是否为Agent调用响应
     */
    public boolean isAgentCallResponse() {
        return getResponseType() == ResponseType.AGENT_CALL;
    }

    /**
     * 获取时间戳，如果为空则返回当前时间
     */
    public LocalDateTime getTimestamp() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        return timestamp;
    }

    /**
     * 转换为ToolResult（向后兼容）
     */
    public ToolResult toToolResult() {
        if (success) {
            return ToolResult.success(toolCallId, content);
        } else {
            return ToolResult.error(toolCallId, error);
        }
    }

    // 静态工厂方法
    public static BaseToolResponse success(String toolCallId, String content) {
        return SimpleToolResponse.builder()
                .toolCallId(toolCallId)
                .success(true)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static BaseToolResponse error(String toolCallId, String error) {
        return SimpleToolResponse.builder()
                .toolCallId(toolCallId)
                .success(false)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 简单工具响应实现
     */
    @Data
    @SuperBuilder
    public static class SimpleToolResponse extends BaseToolResponse {
        @Override
        public ResponseType getResponseType() {
            return isSuccess() ? ResponseType.TEXT : ResponseType.ERROR;
        }
    }
}