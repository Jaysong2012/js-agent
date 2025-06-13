package cn.apmen.jsagent.framework.stream;

import cn.apmen.jsagent.framework.core.AgentResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式响应缓冲器
 * 支持配置驱动的智能缓冲策略
 */
@Data
@Slf4j
public class StreamBuffer {
    private final List<AgentResponse> buffer = new ArrayList<>();
    private final AtomicBoolean toolCallDetected = new AtomicBoolean(false);
    private final AtomicBoolean streamCompleted = new AtomicBoolean(false);
    private final AtomicBoolean bufferReleased = new AtomicBoolean(false);
    /**
     * 是否流式输出工具调用时的内容
     */
    private final boolean streamToolCallContent;
    /**
     * 构造函数
     * @param streamToolCallContent 是否流式输出工具调用时的内容
     */
    public StreamBuffer(boolean streamToolCallContent) {
        this.streamToolCallContent = streamToolCallContent;
    }
    /**
     * 添加响应到缓冲区
     * @param response 响应
     * @return 缓冲决策
     */
    public synchronized BufferDecision addResponse(AgentResponse response) {
        buffer.add(response);

        // 记录工具调用状态
        if (response.getType() == AgentResponse.ResponseType.TOOL_CALL) {
            toolCallDetected.set(true);
            log.debug("Tool call detected, streamToolCallContent={}", streamToolCallContent);
        }

        // 检查是否是最终响应
        if (response.isFinalResponse()) {
            streamCompleted.set(true);
            if (!streamToolCallContent) {
                // 只有在不流式输出时才需要特殊处理
                return BufferDecision.RELEASE_ALL;
            }
        }

        if (streamToolCallContent) {
            // 模式1: 完全不缓冲 - 所有内容立即输出给用户
            log.trace("streamToolCallContent=true, immediate output for all responses");
            return BufferDecision.DIRECT_OUTPUT;
        } else {
            // 模式2: 智能缓冲 - 缓冲所有内容，等流式完成后根据是否有工具调用决定
            log.trace("streamToolCallContent=false, buffering all content (size: {})", buffer.size());
            return BufferDecision.CONTINUE_BUFFERING;
        }
    }
    /**
     * 获取缓冲区内容
     */
    public synchronized List<AgentResponse> getBufferedResponses() {
        return new ArrayList<>(buffer);
    }
    /**
     * 获取非工具调用的文本内容
     */
    public synchronized List<AgentResponse> getTextResponses() {
        List<AgentResponse> textResponses = new ArrayList<>();
        for (AgentResponse response : buffer) {
            if (response.getType() == AgentResponse.ResponseType.TEXT) {
                textResponses.add(response);
            }
        }
        return textResponses;
    }
    /**
     * 清空缓冲区
     */
    public synchronized void clearBuffer() {
        buffer.clear();
    }
    /**
     * 是否检测到工具调用
     */
    public boolean isToolCallDetected() {
        return toolCallDetected.get();
    }
    /**
     * 是否流式响应已完成
     */
    public boolean isStreamCompleted() {
        return streamCompleted.get();
    }
    /**
     * 是否缓冲区已释放
     */
    public boolean isBufferReleased() {
        return bufferReleased.get();
    }
    /**
     * 缓冲区决策枚举
     */
    public enum BufferDecision {
        CONTINUE_BUFFERING,    // 继续缓冲
        RELEASE_ALL,           // 释放所有内容
        DIRECT_OUTPUT          // 直接输出当前响应
    }
}