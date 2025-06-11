package cn.apmen.jsagent.framework.openaiunified.model.request;

/**
 * 工具调用类
 */
public class ToolCall {
    private String id;
    private String type;
    private FunctionCall function;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FunctionCall getFunction() {
        return function;
    }

    public void setFunction(FunctionCall function) {
        this.function = function;
    }
}