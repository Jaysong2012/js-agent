package cn.apmen.jsagent.framework.lifecycle;

import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.core.RunnerContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Agent生命周期管理器
 * 管理所有生命周期钩子的注册和执行
 */
@Slf4j
public class AgentLifecycleManager {
    private final List<AgentLifecycle> lifecycles = new CopyOnWriteArrayList<>();
    /**
     * 注册生命周期钩子
     */
    public void registerLifecycle(AgentLifecycle lifecycle) {
        lifecycles.add(lifecycle);
        // 按优先级排序
        lifecycles.sort(Comparator.comparingInt(AgentLifecycle::getPriority));
        log.info("Registered lifecycle: {} with priority {}", lifecycle.getName(), lifecycle.getPriority());
    }
    /**
     * 批量注册生命周期钩子
     */
    public void registerLifecycles(List<AgentLifecycle> lifecycleList) {
        lifecycleList.forEach(this::registerLifecycle);
    }
    /**
     * 移除生命周期钩子
     */
    public void unregisterLifecycle(String name) {
        lifecycles.removeIf(lifecycle -> lifecycle.getName().equals(name));
        log.info("Unregistered lifecycle: {}", name);
    }
    /**
     * 执行初始化钩子
     */
    public Mono<Void> executeInitialize(RunnerContext context) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onInitialize(context)
                    .doOnSuccess(v -> log.debug("Lifecycle {} initialized", lifecycle.getName()))
                    .onErrorResume(error -> {
                        log.warn("Lifecycle {} initialization failed: {}", lifecycle.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 执行执行前钩子
     */
    public Mono<Void> executeBeforeExecution(RunnerContext context) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onBeforeExecution(context)
                    .doOnSuccess(v -> log.debug("Lifecycle {} before execution completed", lifecycle.getName()))
                    .onErrorResume(error -> {
                        log.warn("Lifecycle {} before execution failed: {}", lifecycle.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 执行执行后钩子
     */
    public Mono<Void> executeAfterExecution(RunnerContext context, AgentResponse response) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onAfterExecution(context, response)
                    .doOnSuccess(v -> log.debug("Lifecycle {} after execution completed", lifecycle.getName()))
                    .onErrorResume(error -> {
                        log.warn("Lifecycle {} after execution failed: {}", lifecycle.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 执行工具调用前钩子
     */
    public Mono<Void> executeBeforeToolCall(RunnerContext context) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onBeforeToolCall(context)
                    .doOnSuccess(v -> log.debug("Lifecycle {} before tool call completed", lifecycle.getName()))
                    .onErrorResume(error -> {
                        log.warn("Lifecycle {} before tool call failed: {}", lifecycle.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 执行工具调用后钩子
     */
    public Mono<Void> executeAfterToolCall(RunnerContext context) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onAfterToolCall(context)
                    .doOnSuccess(v -> log.debug("Lifecycle {} after tool call completed", lifecycle.getName()))
                    .onErrorResume(error -> {
                        log.warn("Lifecycle {} after tool call failed: {}", lifecycle.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 执行完成钩子
     */
    public Mono<Void> executeComplete(RunnerContext context) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onComplete(context)
                    .doOnSuccess(v -> log.debug("Lifecycle {} completion completed", lifecycle.getName()))
                    .onErrorResume(error -> {
                        log.warn("Lifecycle {} completion failed: {}", lifecycle.getName(), error.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 执行异常钩子
     */
    public Mono<Void> executeError(RunnerContext context, Throwable error) {
        return Flux.fromIterable(lifecycles)
                .flatMap(lifecycle -> lifecycle.onError(context, error)
                    .doOnSuccess(v -> log.debug("Lifecycle {} error handling completed", lifecycle.getName()))
                    .onErrorResume(err -> {
                        log.warn("Lifecycle {} error handling failed: {}", lifecycle.getName(), err.getMessage());
                        return Mono.empty();
                    }))
                .then();
    }
    /**
     * 获取所有注册的生命周期名称
     */
    public List<String> getRegisteredLifecycleNames() {
        return lifecycles.stream()
                .map(AgentLifecycle::getName)
                .toList();
    }
}