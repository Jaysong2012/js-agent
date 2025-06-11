package cn.apmen.jsagent.framework.openaiunified.model.response.stream;

/**
 * 函数调用增量类
 */
public class FunctionCallDelta {
    private String name;
    private String arguments;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }
}