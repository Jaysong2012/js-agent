package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.ToolResult;
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

    private ToolCall callTool;

    /**
     * 工具结果列表（当type为TOOL_RESULT时）
     */
    private ToolResult toolResult;

    /**
     * 是否为最终事件
     */
    private boolean isFinal;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 调试信息（当type为DEBUG时）
     */
    private String debugInfo;

    /**
     * 调试级别（当type为DEBUG时）
     */
    private DebugLevel debugLevel;

    /**
     * 事件时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 事件类型枚举
     */
    public enum EventType {
        TEXT_RESPONSE,  // 文本响应 - 回复给用户的内容
        TOOL_CALL,      // 工具调用 - 发起方法调用的Event
        TOOL_RESULT,    // 工具结果 - 工具执行结果的Event
        ERROR,          // 错误 - 异常时候的Event
        DEBUG           // 调试 - 系统关键执行点的Event
    }

    /**
     * 调试级别枚举
     */
    public enum DebugLevel {
        TRACE,   // 跟踪级别
        DEBUG,   // 调试级别
        INFO,    // 信息级别
        WARN,    // 警告级别
        ERROR    // 错误级别
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

    public static AgentEvent toolResult(ToolResult toolResult, ToolCall callTool) {
        return AgentEvent.builder()
                .type(EventType.TOOL_RESULT)
                .toolResult(toolResult)
                .callTool(callTool)
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

    public static AgentEvent debug(String debugInfo, DebugLevel level) {
        return AgentEvent.builder()
                .type(EventType.DEBUG)
                .debugInfo(debugInfo)
                .debugLevel(level)
                .isFinal(false)
                .build();
    }

    public static AgentEvent debug(String debugInfo) {
        return debug(debugInfo, DebugLevel.DEBUG);
    }

    // 便捷的调试事件创建方法
    public static AgentEvent debugTrace(String debugInfo) {
        return debug(debugInfo, DebugLevel.TRACE);
    }

    public static AgentEvent debugInfo(String debugInfo) {
        return debug(debugInfo, DebugLevel.INFO);
    }

    public static AgentEvent debugWarn(String debugInfo) {
        return debug(debugInfo, DebugLevel.WARN);
    }

    public static AgentEvent debugError(String debugInfo) {
        return debug(debugInfo, DebugLevel.ERROR);
    }

    // 兼容旧版本的方法
    @Deprecated
    public static AgentEvent thinking(String content) {
        return debug("THINKING: " + content, DebugLevel.INFO);
    }
}
