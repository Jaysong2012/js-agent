package cn.apmen.jsagent.framework.stream;

import cn.apmen.jsagent.framework.openaiunified.model.request.Function;
import cn.apmen.jsagent.framework.openaiunified.model.request.FunctionCall;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.openaiunified.model.response.stream.MessageDelta;
import cn.apmen.jsagent.framework.openaiunified.model.response.stream.StreamChoice;
import cn.apmen.jsagent.framework.openaiunified.model.response.stream.ToolCallDelta;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式响应累积器
 * 用于累积流式响应片段，构建完整的消息
 */
@Data
@Slf4j
public class StreamAccumulator {
    private StringBuilder contentBuilder = new StringBuilder();
    private List<ToolCall> toolCalls = new ArrayList<>();
    private String role = "assistant";
    private boolean isComplete = false;
    private String newContent = null;
    /**
     * 累积流式响应片段
     */
    public void accumulate(StreamChoice choice) {
        if (choice.getDelta() != null) {
            MessageDelta delta = choice.getDelta();

            // 累积内容
            if (delta.getContent() != null) {
                contentBuilder.append(delta.getContent());
                newContent = delta.getContent(); // 记录新增内容
                log.trace("Accumulated content: {}", delta.getContent());
            } else {
                newContent = null;
            }

            // 累积工具调用
            if (delta.getToolCalls() != null) {
                log.trace("Processing {} tool call deltas", delta.getToolCalls().size());
                for (ToolCallDelta toolCallDelta : delta.getToolCalls()) {
                    log.trace("Tool call delta: index={}, id={}, type={}, function={}",
                        toolCallDelta.getIndex(),
                        toolCallDelta.getId(),
                        toolCallDelta.getType(),
                        toolCallDelta.getFunction() != null ? toolCallDelta.getFunction().getName() : "null");
                    accumulateToolCall(toolCallDelta);
                }
            }

            // 设置角色
            if (delta.getRole() != null) {
                this.role = delta.getRole();
            }
        }

        // 检查是否完成
        if ("stop".equals(choice.getFinishReason()) || "tool_calls".equals(choice.getFinishReason())) {
            isComplete = true;
            log.debug("Stream completed with finish reason: {}, toolCalls count: {}",
                choice.getFinishReason(), toolCalls.size());
        }
    }
    /**
     * 累积工具调用
     */
    private void accumulateToolCall(ToolCallDelta toolCallDelta) {
        // 确保工具调用列表足够大
        while (toolCalls.size() <= toolCallDelta.getIndex()) {
            toolCalls.add(new ToolCall());
        }

        ToolCall toolCall = toolCalls.get(toolCallDelta.getIndex());

        if (toolCallDelta.getId() != null) {
            toolCall.setId(toolCallDelta.getId());
        }

        if (toolCallDelta.getType() != null) {
            toolCall.setType(toolCallDelta.getType());
        }

        if (toolCallDelta.getFunction() != null) {
            if (toolCall.getFunction() == null) {
                toolCall.setFunction(new FunctionCall());
            }

            if (toolCallDelta.getFunction().getName() != null) {
                toolCall.getFunction().setName(toolCallDelta.getFunction().getName());
            }

            if (toolCallDelta.getFunction().getArguments() != null) {
                String existingArgs = toolCall.getFunction().getArguments();
                if (existingArgs == null) {
                    existingArgs = "";
                }
                toolCall.getFunction().setArguments(existingArgs + toolCallDelta.getFunction().getArguments());
            }
        }
    }
    /**
     * 构建完整的消息
     */
    public Message buildMessage() {
        Message message = new Message();
        message.setRole(role);
        message.setContent(contentBuilder.toString());

        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }

        return message;
    }
    /**
     * 是否有新内容
     */
    public boolean hasNewContent() {
        return newContent != null && !newContent.isEmpty();
    }
    /**
     * 获取新增内容
     */
    public String getNewContent() {
        return newContent;
    }
    /**
     * 是否完成
     */
    public boolean isComplete() {
        return isComplete;
    }
}