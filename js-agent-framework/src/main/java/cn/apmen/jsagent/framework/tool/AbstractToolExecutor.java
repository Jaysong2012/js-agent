package cn.apmen.jsagent.framework.tool;

import cn.apmen.jsagent.framework.openaiunified.model.request.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 抽象工具执行器基类
 * 提供工具定义和参数解析的默认实现
 */
@Slf4j
public abstract class AbstractToolExecutor implements ToolExecutor {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 解析工具调用参数
     * @param toolCall 工具调用
     * @return 解析后的参数Map
     */
    protected Map<String, Object> parseArguments(ToolCall toolCall) {

        try {
            if (toolCall.getFunction() == null || toolCall.getFunction().getArguments() == null) {
                return new HashMap<>();
            }

            String argumentsJson = toolCall.getFunction().getArguments();
            JsonNode arguments = objectMapper.readTree(argumentsJson);

            Map<String, Object> result = new HashMap<>();
            arguments.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    result.put(entry.getKey(), value.asText());
                } else if (value.isNumber()) {
                    result.put(entry.getKey(), value.asText());
                } else if (value.isBoolean()) {
                    result.put(entry.getKey(), value.asBoolean());
                } else if (value.isObject() || value.isArray()) {
                    // 对于对象和数组，保持为JsonNode或转换为Map/List
                    try {
                        result.put(entry.getKey(), objectMapper.treeToValue(value, Object.class));
                    } catch (Exception e) {
                        log.warn("Failed to convert JsonNode to Object for key {}: {}", entry.getKey(), e.getMessage());
                        result.put(entry.getKey(), value.toString());
                    }
                } else {
                    result.put(entry.getKey(), value.asText());
                }
            });

            return result;
        } catch (Exception e) {
            log.error("Failed to parse tool arguments: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 获取字符串参数
     * @param arguments 参数Map
     * @param key 参数名
     * @return 参数值
     */
    protected String getStringParameter(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取字符串参数（带默认值）
     * @param arguments 参数Map
     * @param key 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected String getStringParameter(Map<String, Object> arguments, String key, String defaultValue) {
        String value = getStringParameter(arguments, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取整数参数
     * @param arguments 参数Map
     * @param key 参数名
     * @return 参数值
     */
    protected Integer getIntParameter(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) return null;

        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to parse integer parameter {}: {}", key, value);
            return null;
        }
    }

    /**
     * 获取整数参数（带默认值）
     * @param arguments 参数Map
     * @param key 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected Integer getIntParameter(Map<String, Object> arguments, String key, Integer defaultValue) {
        Integer value = getIntParameter(arguments, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 验证必需参数
     * @param arguments 参数Map
     * @param requiredParams 必需参数列表
     * @return 验证结果
     */
    protected boolean validateRequiredParameters(Map<String, Object> arguments, String... requiredParams) {
        for (String param : requiredParams) {
            if (!arguments.containsKey(param) || arguments.get(param) == null) {
                log.warn("Missing required parameter: {}", param);
                return false;
            }
        }
        return true;
    }

    /**
     * 创建成功结果
     * @param toolCallId 工具调用ID
     * @param content 结果内容
     * @return 工具结果
     */
    protected ToolResult success(String toolCallId, String content) {
        return ToolResult.success(toolCallId, content);
    }

    /**
     * 创建错误结果
     * @param toolCallId 工具调用ID
     * @param error 错误信息
     * @return 工具结果
     */
    protected ToolResult error(String toolCallId, String error) {
        return ToolResult.error(toolCallId, error);
    }

    /**
     * 执行工具调用的模板方法
     * @param toolCall 工具调用信息
     * @param context 工具上下文
     * @return 执行结果
     */
    @Override
    public final Mono<ToolResult> execute(ToolCall toolCall, ToolContext context) {
        try {
            // 解析参数
            Map<String, Object> arguments = parseArguments(toolCall);

            // 验证参数
            if (!validateParameters(arguments)) {
                return Mono.just(error(toolCall.getId(), "Invalid parameters"));
            }

            // 执行具体逻辑
            return doExecute(toolCall, context, arguments);

        } catch (Exception e) {
            log.error("Tool execution failed for {}: {}", getToolName(), e.getMessage(), e);
            return Mono.just(error(toolCall.getId(), "Tool execution failed: " + e.getMessage()));
        }
    }

    /**
     * 子类需要实现的具体执行逻辑
     * @param toolCall 工具调用信息
     * @param context 工具上下文
     * @param arguments 解析后的参数
     * @return 执行结果
     */
    protected abstract Mono<ToolResult> doExecute(ToolCall toolCall, ToolContext context, Map<String, Object> arguments);

    /**
     * 默认的参数验证实现
     * 子类可以重写此方法提供自定义验证逻辑
     * @param arguments 参数Map
     * @return 验证结果
     */
    public boolean validateParameters(Map<String, Object> arguments) {
        return validateRequiredParameters(arguments, getRequiredParameters());
    }
}

