package cn.apmen.jsagent.framework.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理AgentException
     */
    @ExceptionHandler(AgentException.class)
    public ResponseEntity<Map<String, Object>> handleAgentException(AgentException e) {
        log.error("Agent exception occurred: [{}] {}", e.getErrorCode().getCode(), e.getMessage(), e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorCode", e.getErrorCode().getCode());
        response.put("errorMessage", e.getErrorCode().getMessage());
        response.put("timestamp", LocalDateTime.now());
        
        // 根据错误级别决定HTTP状态码
        HttpStatus status = mapErrorLevelToHttpStatus(e.getErrorLevel());
        
        // 根据是否为用户错误决定是否暴露详细信息
        if (e.isUserError()) {
            response.put("details", e.getContext());
        } else {
            response.put("details", "系统内部错误，请联系管理员");
        }
        
        // 添加重试信息
        if (e.isRetryable()) {
            response.put("retryable", true);
            response.put("retryAfter", calculateRetryAfter(e.getErrorCode()));
        } else {
            response.put("retryable", false);
        }
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unexpected exception occurred", e);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("errorCode", ErrorCode.SYSTEM_ERROR.getCode());
        response.put("errorMessage", ErrorCode.SYSTEM_ERROR.getMessage());
        response.put("details", "系统内部错误，请稍后重试");
        response.put("timestamp", LocalDateTime.now());
        response.put("retryable", false);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 将错误级别映射为HTTP状态码
     */
    private HttpStatus mapErrorLevelToHttpStatus(ErrorLevel level) {
        switch (level) {
            case WARN:
                return HttpStatus.BAD_REQUEST;
            case ERROR:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case FATAL:
                return HttpStatus.SERVICE_UNAVAILABLE;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
    
    /**
     * 计算重试延迟时间（秒）
     */
    private int calculateRetryAfter(ErrorCode errorCode) {
        switch (errorCode) {
            case LLM_RATE_LIMITED:
                return 60; // 1分钟后重试
            case LLM_TIMEOUT:
            case NETWORK_ERROR:
                return 30; // 30秒后重试
            case TOOL_TIMEOUT:
                return 10; // 10秒后重试
            default:
                return 5; // 默认5秒后重试
        }
    }
}