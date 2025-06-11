package cn.apmen.jsagent.framework.openaiunified.model.response.stream;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 消息增量类
 */
public class MessageDelta {
    private String role;
    private String content;
    
    @JsonProperty("tool_calls")
    private List<ToolCallDelta> toolCalls;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCallDelta> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallDelta> toolCalls) {
        this.toolCalls = toolCalls;
    }
}