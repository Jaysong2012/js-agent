package cn.apmen.jsagent.example.tools;

import cn.apmen.jsagent.framework.tool.ToolExecutor;
import cn.apmen.jsagent.framework.tool.ToolContext;
import cn.apmen.jsagent.framework.tool.ToolResult;
import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 计算器工具 - 支持基本的数学运算
 */
@Component
@Slf4j
public class CalculatorTool implements ToolExecutor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getToolName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "执行基本的数学运算，支持加减乘除";
    }

    @Override
    public Mono<ToolResult> execute(ToolCall toolCall, ToolContext context) {
        try {
            // 解析参数
            String argumentsJson = toolCall.getFunction().getArguments();
            JsonNode arguments = objectMapper.readTree(argumentsJson);
            
            String expression = arguments.get("expression").asText();
            log.info("计算表达式: {}", expression);
            
            // 执行计算
            double result = evaluateExpression(expression);
            
            String resultMessage = String.format("计算结果: %s = %.2f", expression, result);
            
            return Mono.just(ToolResult.success(toolCall.getId(), resultMessage));
            
        } catch (Exception e) {
            log.error("计算器工具执行失败", e);
            return Mono.just(ToolResult.error(toolCall.getId(), "计算失败: " + e.getMessage()));
        }
    }

    /**
     * 简单的表达式计算器
     * 支持基本的四则运算
     */
    private double evaluateExpression(String expression) {
        // 移除空格
        expression = expression.replaceAll("\\s+", "");
        
        // 简单的计算逻辑 - 这里只是示例，实际应用中可以使用更复杂的表达式解析器
        if (expression.contains("+")) {
            String[] parts = expression.split("\\+");
            return Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
        } else if (expression.contains("-")) {
            String[] parts = expression.split("-");
            if (parts.length == 2) {
                return Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]);
            }
        } else if (expression.contains("*")) {
            String[] parts = expression.split("\\*");
            return Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]);
        } else if (expression.contains("/")) {
            String[] parts = expression.split("/");
            double divisor = Double.parseDouble(parts[1]);
            if (divisor == 0) {
                throw new IllegalArgumentException("除数不能为零");
            }
            return Double.parseDouble(parts[0]) / divisor;
        }
        
        // 如果没有运算符，直接返回数字
        return Double.parseDouble(expression);
    }
}