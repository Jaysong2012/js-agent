package cn.apmen.jsagent.framework.openaiunified.model.response;

import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 选择类
 */
public class Choice {
    private int index;
    private Message message;
    
    @JsonProperty("finish_reason")
    private String finishReason;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
}