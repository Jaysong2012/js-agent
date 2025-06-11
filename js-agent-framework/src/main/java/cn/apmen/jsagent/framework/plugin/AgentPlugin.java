package cn.apmen.jsagent.framework.plugin;

import cn.apmen.jsagent.framework.core.RunnerContext;
import reactor.core.publisher.Mono;

/**
 * Agent插件接口
 * 定义插件的基本能力和生命周期
 */
public interface AgentPlugin {
    
    /**
     * 获取插件名称
     */
    String getName();
    
    /**
     * 获取插件版本
     */
    String getVersion();
    
    /**
     * 获取插件描述
     */
    String getDescription();
    
    /**
     * 获取插件优先级（数字越小优先级越高）
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * 插件初始化
     * @param context 插件上下文
     * @return 初始化结果
     */
    Mono<Void> initialize(PluginContext context);
    
    /**
     * 插件启动
     * @param context 插件上下文
     * @return 启动结果
     */
    Mono<Void> start(PluginContext context);
    
    /**
     * 插件停止
     * @param context 插件上下文
     * @return 停止结果
     */
    Mono<Void> stop(PluginContext context);
    
    /**
     * 插件销毁
     * @param context 插件上下文
     * @return 销毁结果
     */
    Mono<Void> destroy(PluginContext context);
    
    /**
     * 检查插件是否支持指定的Agent
     * @param agentId Agent ID
     * @return 是否支持
     */
    default boolean supports(String agentId) {
        return true;
    }
    
    /**
     * 获取插件状态
     */
    PluginStatus getStatus();
    
    /**
     * 插件状态枚举
     */
    enum PluginStatus {
        UNINITIALIZED,  // 未初始化
        INITIALIZED,    // 已初始化
        STARTED,        // 已启动
        STOPPED,        // 已停止
        ERROR           // 错误状态
    }
}