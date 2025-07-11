package cn.apmen.jsagent.framework.openaiunified.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 使用统计类
 */
public class Usage {
    @JsonProperty("prompt_tokens")
    private int promptTokens;
    
    @JsonProperty("completion_tokens")
    private int completionTokens;
    
    @JsonProperty("total_tokens")
    private int totalTokens;

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
}