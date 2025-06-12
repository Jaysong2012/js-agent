package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.core.AgentEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Flux;

/**
 * Agent工具响应类
 * 支持流式响应和直接输出给用户
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResponse extends BaseToolResponse {
    /**
     * 流式事件流（用于流式响应）- 不参与序列化
     */
    @JsonIgnore
    private transient Flux<AgentEvent> eventStream;
    /**
     * 被调用的Agent ID
     */
    private String targetAgentId;
    /**
     * 被调用的Agent名称
     */
    private String targetAgentName;
    /**
     * 是否为流式调用
     */
    private boolean streamCall;
    /**
     * Agent调用的原始请求
     */
    private String originalRequest;

    /**
     * 流式判定字段 - 用于首片段判定是否直接输出
     */
    private Boolean isDirectOutputDecision;

    /**
     * 是否为判定片段
     */
    private boolean isDecisionFragment;
    @Override
    public ResponseType getResponseType() {
        if (!isSuccess()) {
            return ResponseType.ERROR;
        }
        return streamCall ? ResponseType.STREAM : ResponseType.AGENT_CALL;
    }
    /**
     * 创建流式判定片段
     */
    public static AgentToolResponse createDecisionFragment(String toolCallId, boolean directOutput) {
        return AgentToolResponse.builder()
                .toolCallId(toolCallId)
                .success(true)
                .isDirectOutputDecision(directOutput)
                .isDecisionFragment(true)
                .streamCall(true)
                .build();
    }
    /**
     * 创建成功的Agent调用响应（非流式）
     */
    public static AgentToolResponse success(String toolCallId, String targetAgentId,
                                          String targetAgentName, String content) {
        return AgentToolResponse.builder()
                .toolCallId(toolCallId)
                .success(true)
                .content(content)
                .targetAgentId(targetAgentId)
                .targetAgentName(targetAgentName)
                .streamCall(false)
                .build();
    }
    /**
     * 创建成功的Agent调用响应（流式内容片段）
     */
    public static AgentToolResponse streamContent(String toolCallId, String content) {
        return AgentToolResponse.builder()
                .toolCallId(toolCallId)
                .success(true)
                .content(content)
                .streamCall(true)
                .isDecisionFragment(false)
                .build();
    }
    /**
     * 创建失败的Agent调用响应
     */
    public static AgentToolResponse error(String toolCallId, String error) {
        return AgentToolResponse.builder()
                .toolCallId(toolCallId)
                .success(false)
                .error(error)
                .streamCall(false)
                .build();
    }
    /**
     * 设置为直接输出给用户
     */
    public AgentToolResponse withDirectOutput() {
        this.setDirectOutput(true);
        return this;
    }
    /**
     * 获取调用摘要信息
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getCallSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Agent Call: ").append(targetAgentName != null ? targetAgentName : targetAgentId);
        if (streamCall) {
            summary.append(" (Stream)");
        }
        if (isDirectOutput()) {
            summary.append(" [Direct Output]");
        }
        if (isDecisionFragment) {
            summary.append(" [Decision: ").append(isDirectOutputDecision).append("]");
        }
        return summary.toString();
    }

    /**
     * 检查是否有直接输出判定
     */
    public boolean hasDirectOutputDecision() {
        return isDirectOutputDecision != null;
    }
}