package cn.apmen.jsagent.framework.agent;

import cn.apmen.jsagent.framework.core.AgentResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent接口
 * 定义Agent的基本能力，支持流式和非流式调用
 */
public interface Agent {
    
    /**
     * 获取Agent ID
     */
    String getId();
    
    /**
     * 获取Agent名称
     */
    String getName();
    
    /**
     * 获取Agent描述
     */
    String getDescription();
    
    /**
     * 非流式调用
     * @param message 用户消息
     * @return Agent响应
     */
    Mono<AgentResponse> call(String message);
    
    /**
     * 流式调用
     * @param message 用户消息
     * @return Agent响应流
     */
    Flux<AgentResponse> callStream(String message);
    
    /**
     * 是否支持流式调用
     */
    default boolean supportsStreaming() {
        return true;
    }
}