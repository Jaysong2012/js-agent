package cn.apmen.jsagent.framework.tool;

import lombok.Data;

/**
 * 工具执行结果
 */
@Data
public class ToolResult {

    /**
     * 工具调用ID
     */
    private String toolCallId;

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 执行结果内容
     */
    private String content;

    /**
     * 错误信息（如果执行失败）
     */
    private String error;
    /**
     * 执行耗时（毫秒）
     */
    private long executionTimeMs;

    private ToolResult(String toolCallId, boolean success, String content, String error) {
        this.toolCallId = toolCallId;
        this.success = success;
        this.content = content;
        this.error = error;
    }
    /**
     * 创建成功结果
     */
    public static ToolResult success(String toolCallId, String content) {
        return new ToolResult(toolCallId, true, content, null);
    }
    /**
     * 创建失败结果
     */
    public static ToolResult error(String toolCallId, String error) {
        return new ToolResult(toolCallId, false, null, error);
    }
    /**
     * 创建失败结果（带异常）
     */
    public static ToolResult error(String toolCallId, String error, Throwable throwable) {
        String errorMessage = error;
        if (throwable != null) {
            errorMessage += ": " + throwable.getMessage();
        }
        return new ToolResult(toolCallId, false, null, errorMessage);
    }
}