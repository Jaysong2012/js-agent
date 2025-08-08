package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /**
     * 响应类型
     */
    private ResponseType type;

    /**
     * 文本内容（当type为TEXT时）
     */
    private String content;

    /**
     * 工具调用列表（当type为TOOL_CALL时）
     */
    private List<ToolCall> toolCalls;

    /**
     * 工具结果（当type为TOOL_RESULT时）
     */
    private ToolResult toolResult;

    private ToolCall callTool;

    /**
     * 是否为最终响应
     */
    private boolean isFinalResponse;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 响应类型枚举
     */
    public enum ResponseType {
        TEXT,           // 文本响应
        TOOL_CALL,      // 工具调用
        TOOL_RESULT,    // 工具结果
        ERROR,          // 错误
        THINKING,       // 思考中（流式响应的中间状态）
        DEBUG           // 调试信息
    }

    // 静态工厂方法
    public static AgentResponse text(String content, boolean isFinal) {
        return AgentResponse.builder()
                .type(ResponseType.TEXT)
                .content(content)
                .isFinalResponse(isFinal)
                .build();
    }

    public static AgentResponse toolCall(List<ToolCall> toolCalls) {
        return AgentResponse.builder()
                .type(ResponseType.TOOL_CALL)
                .toolCalls(toolCalls)
                .isFinalResponse(false)
                .build();
    }

    public static AgentResponse toolResult(ToolResult toolResult, ToolCall callTool) {
        return AgentResponse.builder()
                .type(ResponseType.TOOL_RESULT)
                .callTool(callTool)
                .toolResult(toolResult)
                .isFinalResponse(false)
                .build();
    }

    public static AgentResponse error(String error) {
        return AgentResponse.builder()
                .type(ResponseType.ERROR)
                .error(error)
                .isFinalResponse(true)
                .build();
    }

    public static AgentResponse thinking(String content) {
        return AgentResponse.builder()
                .type(ResponseType.THINKING)
                .content(content)
                .isFinalResponse(false)
                .build();
    }

    public static AgentResponse debug(String content) {
        return AgentResponse.builder()
                .type(ResponseType.DEBUG)
                .content(content)
                .isFinalResponse(false)
                .build();
    }
}
