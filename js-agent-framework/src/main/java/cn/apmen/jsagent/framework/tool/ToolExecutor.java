package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import reactor.core.publisher.Mono;

/**
 * 工具执行器接口
 * 定义工具的执行能力
 */
public interface ToolExecutor {

    /**
     * 获取工具名称
     */
    String getToolName();
    /**
     * 获取工具描述
     */
    String getDescription();
    /**
     * 执行工具调用
     * @param toolCall 工具调用信息
     * @param context 工具上下文
     * @return 执行结果
     */
    Mono<ToolResult> execute(ToolCall toolCall, ToolContext context);
    /**
     * 检查是否支持指定的工具调用
     * @param toolCall 工具调用信息
     * @return 是否支持
     */
    default boolean supports(ToolCall toolCall) {
        return toolCall != null && 
               toolCall.getFunction() != null && 
               getToolName().equals(toolCall.getFunction().getName());
    }
}