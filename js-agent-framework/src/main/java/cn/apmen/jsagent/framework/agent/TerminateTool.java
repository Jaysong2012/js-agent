package cn.apmen.jsagent.framework.agent;

import cn.apmen.jsagent.framework.enums.AgentStateEnum;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.AbstractToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 终止工具 - 用于终止Agent对话
 */
public class TerminateTool extends AbstractToolExecutor {

    private final BaseAgent agent;

    public TerminateTool(BaseAgent agent) {
        this.agent = agent;
    }

    @Override
    public String getToolName() {
        return "terminate";
    }

    @Override
    public String getDescription() {
        return "终止当前对话，当任务完成或需要结束对话时使用";
    }

    @Override
    public Map<String, Object> getParametersDefinition() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // reason 参数定义（可选）
        Map<String, Object> reasonProperty = new HashMap<>();
        reasonProperty.put("type", "string");
        reasonProperty.put("description", "终止对话的原因，例如：任务完成、用户要求结束等");
        properties.put("reason", reasonProperty);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{}); // 没有必需参数

        return parameters;
    }

    @Override
    public String[] getRequiredParameters() {
        return new String[]{}; // 没有必需参数
    }

    @Override
    protected Mono<ToolResult> doExecute(ToolCall toolCall, ToolContext context, Map<String, Object> arguments) {
        String reason = getStringParameter(arguments, "reason", "对话正常结束");

        // 设置Agent状态为已完成
        if (agent != null) {
            agent.state = AgentStateEnum.FINISHED;
        }

        String message = String.format("对话已终止。原因: %s", reason);

        return Mono.just(success(toolCall.getId(), message));
    }
}
