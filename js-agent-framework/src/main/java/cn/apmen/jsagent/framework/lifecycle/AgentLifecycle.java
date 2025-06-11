package cn.apmen.jsagent.framework.lifecycle;

import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.core.RunnerContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Agent生命周期管理接口
 * 定义Agent执行的各个阶段和钩子
 */
public interface AgentLifecycle {
    
    /**
     * Agent初始化阶段
     * @param context 运行上下文
     * @return 初始化结果
     */
    Mono<Void> onInitialize(RunnerContext context);
    
    /**
     * Agent执行前钩子
     * @param context 运行上下文
     * @return 预处理结果
     */
    Mono<Void> onBeforeExecution(RunnerContext context);
    
    /**
     * Agent执行后钩子
     * @param context 运行上下文
     * @param response Agent响应
     * @return 后处理结果
     */
    Mono<Void> onAfterExecution(RunnerContext context, AgentResponse response);
    
    /**
     * 工具调用前钩子
     * @param context 运行上下文
     * @return 预处理结果
     */
    Mono<Void> onBeforeToolCall(RunnerContext context);
    
    /**
     * 工具调用后钩子
     * @param context 运行上下文
     * @return 后处理结果
     */
    Mono<Void> onAfterToolCall(RunnerContext context);
    
    /**
     * Agent执行完成钩子
     * @param context 运行上下文
     * @return 清理结果
     */
    Mono<Void> onComplete(RunnerContext context);
    
    /**
     * Agent执行异常钩子
     * @param context 运行上下文
     * @param error 异常信息
     * @return 异常处理结果
     */
    Mono<Void> onError(RunnerContext context, Throwable error);
    
    /**
     * 获取生命周期名称
     */
    String getName();
    
    /**
     * 获取优先级（数字越小优先级越高）
     */
    default int getPriority() {
        return 100;
    }
}