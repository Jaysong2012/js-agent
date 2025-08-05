package cn.apmen.jsagent.example.tools;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import cn.apmen.jsagent.framework.tool.AbstractToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;

/**
 * 计算器工具 - 支持基本的数学运算
 */
@Component
@Slf4j
public class CalculatorTool extends AbstractToolExecutor {

    private final ScriptEngine scriptEngine;

    public CalculatorTool() {
        ScriptEngineManager manager = new ScriptEngineManager();
        this.scriptEngine = manager.getEngineByName("JavaScript");
    }

    @Override
    public String getToolName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "执行基本的数学运算，支持加减乘除";
    }

    @Override
    public Map<String, Object> getParametersDefinition() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
            
        Map<String, Object> properties = new HashMap<>();
            
        // expression 参数定义
        Map<String, Object> expressionProperty = new HashMap<>();
        expressionProperty.put("type", "string");
        expressionProperty.put("description", "要计算的数学表达式，例如：25+17, 100/4, 50*2");
        properties.put("expression", expressionProperty);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"expression"});

        return parameters;
    }

    @Override
    public String[] getRequiredParameters() {
        return new String[]{"expression"};
    }

    @Override
    protected Mono<ToolResult> doExecute(ToolCall toolCall, ToolContext context, Map<String, Object> arguments) {
        String expression = getStringParameter(arguments, "expression");
        log.info("计算表达式: {}", expression);

        try {
            // 执行计算
            double result = evaluateExpression(expression);
            String resultMessage = String.format("计算结果: %s = %.2f", expression, result);
            
            return Mono.just(success(toolCall.getId(), resultMessage));
            
        } catch (Exception e) {
            log.error("计算器工具执行失败", e);
            return Mono.just(error(toolCall.getId(), "计算失败: " + e.getMessage()));
        }
    }

    /**
     * 执行数学表达式计算
     */
    private double evaluateExpression(String expression) throws Exception {
        // 简单的安全检查，只允许数字和基本运算符
        if (!expression.matches("[0-9+\\-*/().\\s]+")) {
            throw new IllegalArgumentException("表达式包含不支持的字符");
        }
        
        Object result = scriptEngine.eval(expression);
        if (result instanceof Number) {
            return ((Number) result).doubleValue();
        } else {
            throw new IllegalArgumentException("计算结果不是数字");
        }
    }
}