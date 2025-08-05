package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.Function;

import java.util.Map;

/**
 * 工具定义接口
 * 提供工具的完整定义信息，包括参数结构
 */
public interface ToolDefinition {

    /**
     * 获取工具名称
     */
    String getToolName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 获取工具参数定义
     */
    Map<String, Object> getParametersDefinition();

    /**
     * 获取必需参数列表
     */
    String[] getRequiredParameters();

    /**
     * 构建Tool对象
     */
    default Tool buildTool() {
        Tool tool = new Tool();
        tool.setType("function");

        Function function = new Function();
        function.setName(getToolName());
        function.setDescription(getDescription());
        function.setParameters(getParametersDefinition());

        tool.setFunction(function);
        return tool;
    }

    /**
     * 验证参数是否有效
     */
    default boolean validateParameters(Map<String, Object> parameters) {
        if (parameters == null) {
            return getRequiredParameters().length == 0;
        }

        // 检查必需参数是否都存在
        for (String required : getRequiredParameters()) {
            if (!parameters.containsKey(required)) {
                return false;
            }
        }

        return true;
    }
}

