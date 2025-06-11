package cn.apmen.jsagent.framework.openaiunified.model.response.stream;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 流式选择类
 */
public class StreamChoice {
    private int index;
    private MessageDelta delta;
    
    @JsonProperty("finish_reason")
    private String finishReason;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public MessageDelta getDelta() {
        return delta;
    }

    public void setDelta(MessageDelta delta) {
        this.delta = delta;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
}