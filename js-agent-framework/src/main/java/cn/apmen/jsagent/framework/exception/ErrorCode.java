package cn.apmen.jsagent.framework.exception;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {
    
    // LLM相关错误 (1000-1999)
    LLM_CALL_FAILED("1001", "LLM调用失败", ErrorLevel.ERROR, true, false),
    LLM_TIMEOUT("1002", "LLM调用超时", ErrorLevel.WARN, true, false),
    LLM_RATE_LIMITED("1003", "LLM调用频率限制", ErrorLevel.WARN, true, false),
    LLM_INVALID_RESPONSE("1004", "LLM响应格式无效", ErrorLevel.ERROR, false, false),
    
    // 工具调用相关错误 (2000-2999)
    TOOL_EXECUTION_FAILED("2001", "工具执行失败", ErrorLevel.ERROR, true, false),
    TOOL_NOT_FOUND("2002", "工具未找到", ErrorLevel.ERROR, false, true),
    TOOL_INVALID_ARGUMENTS("2003", "工具参数无效", ErrorLevel.WARN, false, true),
    TOOL_TIMEOUT("2004", "工具执行超时", ErrorLevel.WARN, true, false),
    TOOL_PERMISSION_DENIED("2005", "工具权限不足", ErrorLevel.ERROR, false, true),
    
    // 流式处理相关错误 (3000-3999)
    STREAM_PARSING_FAILED("3001", "流式数据解析失败", ErrorLevel.WARN, false, false),
    STREAM_CONNECTION_LOST("3002", "流式连接丢失", ErrorLevel.ERROR, true, false),
    STREAM_BUFFER_OVERFLOW("3003", "流式缓冲区溢出", ErrorLevel.ERROR, false, false),
    
    // 上下文相关错误 (4000-4999)
    CONTEXT_BUILD_FAILED("4001", "上下文构建失败", ErrorLevel.ERROR, false, false),
    CONTEXT_INVALID_STATE("4002", "上下文状态无效", ErrorLevel.ERROR, false, false),
    CONTEXT_MAX_ROUNDS_EXCEEDED("4003", "超过最大轮次限制", ErrorLevel.WARN, false, true),
    
    // Agent相关错误 (5000-5999)
    AGENT_NOT_FOUND("5001", "Agent未找到", ErrorLevel.ERROR, false, true),
    AGENT_INITIALIZATION_FAILED("5002", "Agent初始化失败", ErrorLevel.ERROR, false, false),
    AGENT_EXECUTION_FAILED("5003", "Agent执行失败", ErrorLevel.ERROR, true, false),
    AGENT_CIRCULAR_DEPENDENCY("5004", "Agent循环依赖", ErrorLevel.ERROR, false, false),
    
    // 配置相关错误 (6000-6999)
    CONFIG_INVALID("6001", "配置无效", ErrorLevel.ERROR, false, true),
    CONFIG_MISSING("6002", "配置缺失", ErrorLevel.ERROR, false, true),
    
    // 系统相关错误 (9000-9999)
    SYSTEM_ERROR("9001", "系统内部错误", ErrorLevel.ERROR, false, false),
    RESOURCE_EXHAUSTED("9002", "资源耗尽", ErrorLevel.ERROR, true, false),
    NETWORK_ERROR("9003", "网络错误", ErrorLevel.WARN, true, false);
    
    private final String code;
    private final String message;
    private final ErrorLevel level;
    private final boolean retryable;
    private final boolean userError;
    
    ErrorCode(String code, String message, ErrorLevel level, boolean retryable, boolean userError) {
        this.code = code;
        this.message = message;
        this.level = level;
        this.retryable = retryable;
        this.userError = userError;
    }
}