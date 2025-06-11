package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import reactor.core.publisher.Flux;

/**
 * 流式工具执行器接口
 * 支持流式输出的工具需要实现此接口
 */
public interface StreamingToolExecutor extends ToolExecutor {
    
    /**
     * 流式执行工具调用
     * 第一个片段必须包含isDirectOutputDecision判定
     * @param toolCall 工具调用信息
     * @param toolContext 工具执行上下文
     * @return 流式工具响应
     */
    Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext toolContext);
    
    /**
     * 是否支持流式执行
     * @return 是否支持流式
     */
    default boolean supportsStreaming() {
        return true;
    }
}