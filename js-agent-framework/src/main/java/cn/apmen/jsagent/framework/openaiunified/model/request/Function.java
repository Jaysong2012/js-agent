package cn.apmen.jsagent.framework.openaiunified.model.request;

import java.util.Map;

/**
 * 函数定义类
 */
public class Function {
    private String name;
    private String description;
    private Map<String, Object> parameters;

    public Function() {
    }

    public Function(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}