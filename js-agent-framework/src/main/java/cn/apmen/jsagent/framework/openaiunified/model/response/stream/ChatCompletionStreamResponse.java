package cn.apmen.jsagent.framework.openaiunified.model.response.stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 流式ChatCompletions响应类
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionStreamResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<StreamChoice> choices;
    private String content;
    private Boolean lastOne;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<StreamChoice> getChoices() {
        return choices;
    }

    public void setChoices(List<StreamChoice> choices) {
        this.choices = choices;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getLastOne() {
        return lastOne;
    }

    public void setLastOne(Boolean lastOne) {
        this.lastOne = lastOne;
    }
}