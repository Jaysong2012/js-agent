package cn.apmen.jsagent.framework.agent;

import cn.apmen.jsagent.framework.core.AgentRequest;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.enums.AgentStateEnum;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.memory.AgentMemoryService;
import cn.apmen.jsagent.framework.memory.MemoryStats;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强的BaseAgent，集成MemoryService进行记忆管理
 */
@Slf4j
public abstract class BaseAgent implements Agent {

    protected String id;
    protected String name;
    protected String description;
    protected String systemPrompt;
    protected Integer maxSteps;
    protected Integer currentStep = 0;
    protected AgentStateEnum state = AgentStateEnum.IDLE;

    // Memory管理相关
    protected AgentMemoryService memoryService;
    protected String sessionId;
    protected int maxContextTokens = 4000; // 默认上下文token限制

    /**
     * 设置记忆服务
     */
    public void setMemoryService(AgentMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 设置会话ID
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 添加消息到记忆
     */
    protected Mono<Void> addMessageToMemory(Message message) {
        if (memoryService != null && sessionId != null) {
            return memoryService.addMessage(id, sessionId, message)
                .doOnError(error -> log.warn("Failed to add message to memory: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty()); // 记忆失败不影响主流程
        }
        return Mono.empty();
    }

    /**
     * 获取短期记忆（最近的消息）
     */
    protected Mono<List<Message>> getShortTermMemory(int limit) {
        if (memoryService != null && sessionId != null) {
            return memoryService.getShortTermMemory(id, sessionId, limit)
                .doOnError(error -> log.warn("Failed to get short term memory: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
        }
        return Mono.just(new ArrayList<>());
    }

    /**
     * 获取长期记忆（完整历史）
     */
    protected Mono<List<Message>> getLongTermMemory() {
        if (memoryService != null && sessionId != null) {
            return memoryService.getLongTermMemory(id, sessionId)
                .doOnError(error -> log.warn("Failed to get long term memory: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
        }
        return Mono.just(new ArrayList<>());
    }

    /**
     * 获取适合上下文窗口的记忆
     */
    protected Mono<List<Message>> getContextWindowMessages(int maxTokens) {
        if (memoryService != null && sessionId != null) {
            return memoryService.getContextMemory(id, sessionId, maxTokens, systemPrompt)
                .doOnError(error -> log.warn("Failed to get context memory: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
        }
        return Mono.just(new ArrayList<>());
    }

    /**
     * 清空会话记忆
     */
    protected Mono<Void> clearMemory() {
        if (memoryService != null && sessionId != null) {
            return memoryService.clearSessionMemory(id, sessionId)
                .doOnError(error -> log.warn("Failed to clear memory: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty());
        }
        return Mono.empty();
    }

    /**
     * 获取记忆统计信息
     */
    protected Mono<MemoryStats> getMemoryStats() {
        if (memoryService != null && sessionId != null) {
            return memoryService.getMemoryStats(id, sessionId)
                .doOnError(error -> log.warn("Failed to get memory stats: {}", error.getMessage()))
                .onErrorReturn(MemoryStats.builder().totalMessages(0).build());
        }
        return Mono.just(MemoryStats.builder().totalMessages(0).build());
    }

    /**
     * 搜索记忆
     */
    protected Mono<List<Message>> searchMemory(String query, int limit) {
        if (memoryService != null && sessionId != null) {
            return memoryService.searchMemory(id, sessionId, query, limit)
                .doOnError(error -> log.warn("Failed to search memory: {}", error.getMessage()))
                .onErrorReturn(new ArrayList<>());
        }
        return Mono.just(new ArrayList<>());
    }

    /**
     * 增强的运行方法，集成内存管理
     */
    protected Flux<AgentResponse> run(AgentRequest agentRequest) {
        if (state != AgentStateEnum.IDLE) {
            return Flux.error(new AgentException(ErrorCode.AGENT_EXECUTION_FAILED, "Agent is not in idle state"));
        }

        state = AgentStateEnum.RUNNING;
        currentStep = 0;

        return Flux.defer(() -> {
            if (state == AgentStateEnum.RUNNING && currentStep < maxSteps) {
                return step(agentRequest)
                    .doOnNext(response -> {
                        // 自动保存助手响应到内存
                        if (response.getType() == AgentResponse.ResponseType.TEXT && response.getContent() != null) {
                            addMessageToMemory(new Message("assistant", response.getContent()))
                                .subscribe();
                        }
                        currentStep++;
                    })
                    .concatWith(Flux.defer(() -> {
                        if (state == AgentStateEnum.RUNNING && currentStep < maxSteps) {
                            return run(agentRequest);
                        }
                        return Flux.empty();
                    }));
            }
            return Flux.empty();
        });
    }

    /**
     * 重置Agent状态
     */
    public void reset() {
        state = AgentStateEnum.IDLE;
        currentStep = 0;
    }

    /**
     * 获取当前状态
     */
    public AgentStateEnum getState() {
        return state;
    }

    /**
     * 设置状态
     */
    public void setState(AgentStateEnum state) {
        this.state = state;
    }

    /**
     * 获取当前步数
     */
    public Integer getCurrentStep() {
        return currentStep;
    }

    /**
     * 获取最大上下文token数
     */
    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    /**
     * 设置最大上下文token数
     */
    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    abstract Flux<AgentResponse> step(AgentRequest agentRequest);

}
