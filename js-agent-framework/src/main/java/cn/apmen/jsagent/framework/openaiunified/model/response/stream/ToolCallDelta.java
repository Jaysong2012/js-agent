package cn.apmen.jsagent.framework.openaiunified.model.response.stream;

/**
 * 工具调用增量类
 */
public class ToolCallDelta {
    private int index;
    private String id;
    private String type;
    private FunctionCallDelta function;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

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

    public FunctionCallDelta getFunction() {
        return function;
    }

    public void setFunction(FunctionCallDelta function) {
        this.function = function;
    }
}