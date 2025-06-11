package cn.apmen.jsagent.framework.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent配置类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    /**
     * 最大执行轮次（防止无限循环）
     */
    @Builder.Default
    private Integer maxRounds = 10;

    /**
     * 是否启用流式响应
     */
    @Builder.Default
    private Boolean enableStreaming = true;

    /**
     * 超时时间（秒）
     */
    @Builder.Default
    private Integer timeoutSeconds = 300;

    /**
     * 是否启用工具调用
     */
    @Builder.Default
    private Boolean enableToolCalls = true;

    /**
     * 是否启用调试模式
     */
    @Builder.Default
    private Boolean debugMode = false;

    /**
     * 工具调用时是否流式输出模型内容
     * true: 立即流式输出模型内容，即使后续有工具调用
     * false: 等待完整响应，如果有工具调用则忽略模型内容，否则输出全部内容
     */
    @Builder.Default
    private Boolean streamToolCallContent = true;

    private Integer maxContextTokens;

}
