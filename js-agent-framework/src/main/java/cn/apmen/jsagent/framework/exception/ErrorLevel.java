package cn.apmen.jsagent.framework.exception;

/**
 * 错误级别枚举
 */
public enum ErrorLevel {
    /**
     * 调试级别 - 仅用于开发调试
     */
    DEBUG,
    
    /**
     * 信息级别 - 正常的信息记录
     */
    INFO,
    
    /**
     * 警告级别 - 可能的问题，但不影响主要功能
     */
    WARN,
    
    /**
     * 错误级别 - 影响功能的错误
     */
    ERROR,
    
    /**
     * 致命级别 - 导致系统无法继续运行的错误
     */
    FATAL
}