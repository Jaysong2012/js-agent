package cn.apmen.jsagent.framework.exception;

import lombok.Getter;

/**
 * Agent框架统一异常类
 */
@Getter
public class AgentException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final String context;
    private final Object[] args;
    
    public AgentException(ErrorCode errorCode, String context) {
        super(String.format("[%s] %s: %s", errorCode.getCode(), errorCode.getMessage(), context));
        this.errorCode = errorCode;
        this.context = context;
        this.args = null;
    }
    
    public AgentException(ErrorCode errorCode, String context, Throwable cause) {
        super(String.format("[%s] %s: %s", errorCode.getCode(), errorCode.getMessage(), context), cause);
        this.errorCode = errorCode;
        this.context = context;
        this.args = null;
    }
    
    public AgentException(ErrorCode errorCode, String context, Object... args) {
        super(String.format("[%s] %s: %s", errorCode.getCode(), errorCode.getMessage(), 
            String.format(context, args)));
        this.errorCode = errorCode;
        this.context = context;
        this.args = args;
    }
    
    public AgentException(ErrorCode errorCode, Throwable cause, String context, Object... args) {
        super(String.format("[%s] %s: %s", errorCode.getCode(), errorCode.getMessage(), 
            String.format(context, args)), cause);
        this.errorCode = errorCode;
        this.context = context;
        this.args = args;
    }
    
    /**
     * 是否为可重试的错误
     */
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }
    
    /**
     * 是否为用户错误
     */
    public boolean isUserError() {
        return errorCode.isUserError();
    }
    
    /**
     * 获取错误级别
     */
    public ErrorLevel getErrorLevel() {
        return errorCode.getLevel();
    }
}