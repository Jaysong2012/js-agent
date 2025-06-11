package cn.apmen.jsagent.framework.lifecycle.impl;

import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.core.RunnerContext;
import cn.apmen.jsagent.framework.lifecycle.AgentLifecycle;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 日志记录生命周期钩子
 * 示例实现，展示如何使用生命周期管理
 */
@Slf4j
public class LoggingLifecycle implements AgentLifecycle {
    @Override
    public String getName() {
        return "LoggingLifecycle";
    }
    @Override
    public int getPriority() {
        return 10; // 高优先级，早执行
    }
    @Override
    public Mono<Void> onInitialize(RunnerContext context) {
        return Mono.fromRunnable(() -> {
            log.info("Agent execution initialized - ExecutionId: {}, AgentId: {}, UserId: {}, ConversationId: {}", 
                context.getExecutionId(), 
                context.getAgentId(), 
                context.getUserId(), 
                context.getConversationId());
            // 记录开始时间
            context.setExecutionMetadata("startTime", System.currentTimeMillis());
        });
    }
    @Override
    public Mono<Void> onBeforeExecution(RunnerContext context) {
        return Mono.fromRunnable(() -> {
            log.debug("Agent execution starting - Round: {}", context.getCurrentRound());
        });
    }
    @Override
    public Mono<Void> onAfterExecution(RunnerContext context, AgentResponse response) {
        return Mono.fromRunnable(() -> {
            log.debug("Agent execution completed - Round: {}, ResponseType: {}", 
                context.getCurrentRound(), 
                response.getType());
            // 更新执行指标
            if (context.getExecutionMetrics() != null) {
                context.getExecutionMetrics().incrementLlmCalls();
            }
        });
    }
    @Override
    public Mono<Void> onBeforeToolCall(RunnerContext context) {
        return Mono.fromRunnable(() -> {
            log.debug("Tool call starting - Round: {}", context.getCurrentRound());
        });
    }
    @Override
    public Mono<Void> onAfterToolCall(RunnerContext context) {
        return Mono.fromRunnable(() -> {
            log.debug("Tool call completed - Round: {}", context.getCurrentRound());
            // 更新执行指标
            if (context.getExecutionMetrics() != null) {
                context.getExecutionMetrics().incrementToolCalls();
            }
        });
    }
    @Override
    public Mono<Void> onComplete(RunnerContext context) {
        return Mono.fromRunnable(() -> {
            Long startTime = context.getExecutionMetadata("startTime", Long.class);
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("Agent execution completed - ExecutionId: {}, Duration: {}ms, Rounds: {}", 
                    context.getExecutionId(), 
                    duration, 
                    context.getCurrentRound());
                // 更新执行指标
                if (context.getExecutionMetrics() != null) {
                    context.getExecutionMetrics().setExecutionTime(startTime);
                }
            }
        });
    }
    @Override
    public Mono<Void> onError(RunnerContext context, Throwable error) {
        return Mono.fromRunnable(() -> {
            log.error("Agent execution failed - ExecutionId: {}, Error: {}", 
                context.getExecutionId(), 
                error.getMessage(), 
                error);
        });
    }
}