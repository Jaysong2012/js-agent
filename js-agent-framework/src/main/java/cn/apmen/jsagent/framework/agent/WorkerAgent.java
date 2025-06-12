package cn.apmen.jsagent.framework.agent;

import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.llm.LlmConfig;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.openaiunified.model.request.ChatCompletionRequest;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.response.ChatCompletionResponse;
import cn.apmen.jsagent.framework.openaiunified.model.response.Choice;
import cn.apmen.jsagent.framework.openaiunified.model.response.stream.StreamChoice;
import cn.apmen.jsagent.framework.stream.SSEParser;
import cn.apmen.jsagent.framework.stream.StreamAccumulator;
import cn.apmen.jsagent.framework.tool.ToolContext;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * WorkerAgent - 独立的工作Agent
 * 利用ToolContext获取上下文信息，只做一轮QA
 * 可以被AgentTool注册和调用
 */
@Data
@Builder
@Slf4j
public class WorkerAgent implements Agent {

    private final String id;
    private final String name;
    private String description;
    private String systemPrompt;

    // 直接使用LLM客户端
    private final OpenAIUnifiedChatClient llmClient;
    @Builder.Default
    private final LlmConfig llmConfig = createDefaultLlmConfig();

    // SSE解析器
    @Builder.Default
    private final SSEParser sseParser = new SSEParser();

    // 当前调用的上下文（从AgentTool传入）
    private ToolContext currentContext;

    @Override
    public Mono<AgentResponse> call(String message) {
        try {
            // 构建ChatCompletionRequest
            ChatCompletionRequest request = buildChatRequest(message);

            // 直接调用LLM API
            return llmClient.createChatCompletion(request)
                    .map(this::parseResponse)
                    .doOnNext(response -> log.debug("WorkerAgent {} response: {}", name, response.getContent()))
                    .onErrorMap(this::mapToAgentException);

        } catch (Exception e) {
            log.error("Error in WorkerAgent call", e);
            throw new AgentException(
                ErrorCode.AGENT_EXECUTION_FAILED,
                "WorkerAgent call failed for: " + name, e);
        }
    }

    @Override
    public Flux<AgentResponse> callStream(String message) {
        try {
            // 构建ChatCompletionRequest
            ChatCompletionRequest request = buildChatRequest(message);
            request.setStream(true);

            // 使用SSE解析器处理流式响应
            return llmClient.createChatCompletionStream(request)
                    .transform(sseParser::parseSSEStream)
                    .scan(new StreamAccumulator(), (accumulator, streamResponse) -> {
                        if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
                            StreamChoice choice = streamResponse.getChoices().get(0);
                            accumulator.accumulate(choice);
                        }
                        return accumulator;
                    })
                    .flatMap(accumulator -> {
                        // 处理内容片段
                        if (accumulator.hasNewContent()) {
                            String newContent = accumulator.getNewContent();
                            return Flux.just(AgentResponse.text(newContent, false));
                        }

                        // 处理完成事件
                        if (accumulator.isComplete()) {
                            String content = accumulator.buildMessage().getContent();
                            if (content != null && !content.isEmpty()) {
                                return Flux.just(AgentResponse.text("", true));
                            }
                        }

                        return Flux.empty();
                    })
                    .doOnNext(response -> log.debug("WorkerAgent {} stream response: {}", name, response.getContent()))
                    .onErrorMap(this::mapToAgentException);

        } catch (Exception e) {
            log.error("Error in WorkerAgent stream call", e);
            throw new AgentException(
                ErrorCode.AGENT_EXECUTION_FAILED,
                "WorkerAgent stream call failed for: " + name, e);
        }
    }
    /**
     * 设置当前调用的上下文（由AgentTool调用时传入）
     */
    public WorkerAgent withContext(ToolContext context) {
        this.currentContext = context;
        return this;
    }

    /**
     * 构建ChatCompletionRequest
     * 利用ToolContext中的信息
     */
    private ChatCompletionRequest buildChatRequest(String message) {
        ChatCompletionRequest request = new ChatCompletionRequest();

        // 设置模型
        String model = (llmConfig != null && llmConfig.getModel() != null)
                ? llmConfig.getModel()
                : "anthropic.claude-sonnet-4";
        request.setModel(model);

        // 构建消息列表
        List<Message> messages = buildMessages(message);
        request.setMessages(messages);

        // 设置其他参数
        if (llmConfig != null) {
            request.setTemperature(llmConfig.getTemperature());
            request.setMaxTokens(llmConfig.getMaxTokens());
        }

        return request;
    }

    /**
     * 构建消息列表，利用ToolContext中的上下文信息
     */
    private List<Message> buildMessages(String message) {
        List<Message> messages = new ArrayList<>();

        // 1. 添加系统提示词
        String effectiveSystemPrompt = buildEffectiveSystemPrompt();
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.trim().isEmpty()) {
            messages.add(new Message("system", effectiveSystemPrompt));
        }

        // 2. 如果有ToolContext，可以利用其中的历史信息
        if (currentContext != null) {
            // 可以选择性地添加一些上下文信息
            String contextInfo = buildContextInfo();
            if (contextInfo != null && !contextInfo.trim().isEmpty()) {
                messages.add(new Message("system", contextInfo));
            }
        }

        // 3. 添加当前用户消息
        messages.add(new Message("user", message));

        return messages;
    }

    /**
     * 构建有效的系统提示词
     * 可以结合WorkerAgent自身的systemPrompt和上下文信息
     */
    private String buildEffectiveSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        // 添加WorkerAgent自身的系统提示词
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            prompt.append(systemPrompt);
        }

        // 如果有ToolContext，可以添加一些上下文相关的提示
        if (currentContext != null) {
            if (prompt.length() > 0) {
                prompt.append("\n\n");
            }
            prompt.append("当前对话上下文：");
            prompt.append("用户ID: ").append(currentContext.getUserId());
            prompt.append(", 对话ID: ").append(currentContext.getConversationId());
            prompt.append(", 当前轮次: ").append(currentContext.getCurrentRound());
        }

        return prompt.toString();
    }
    /**
     * 构建上下文信息
     * 可以从ToolContext中提取有用的信息
     */
    private String buildContextInfo() {
        if (currentContext == null) {
            return null;
        }

        StringBuilder contextInfo = new StringBuilder();

        // 可以选择性地添加一些历史消息摘要
        List<Message> history = currentContext.getRunnerContext().getMessageHistory();
        if (history != null && !history.isEmpty()) {
            contextInfo.append("最近的对话历史摘要：");
            // 只取最后几条消息作为上下文
            int maxHistoryCount = 3;
            int startIndex = Math.max(0, history.size() - maxHistoryCount);
            for (int i = startIndex; i < history.size(); i++) {
                Message msg = history.get(i);
                if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                    contextInfo.append("\n").append(msg.getRole()).append(": ").append(msg.getContent());
                }
            }
        }

        return contextInfo.toString();
    }

    /**
     * 解析非流式响应
     */
    private AgentResponse parseResponse(ChatCompletionResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return AgentResponse.error("No response choices available");
        }

        Choice choice = response.getChoices().get(0);
        Message message = choice.getMessage();

        if (message == null) {
            return AgentResponse.error("No message in response");
        }

        // WorkerAgent只做文本响应，不处理工具调用
        String content = message.getContent() != null ? message.getContent() : "";
        return AgentResponse.text(content, true);
    }

    /**
     * 创建默认LLM配置
     */
    private static LlmConfig createDefaultLlmConfig() {
        return LlmConfig.builder()
                .model("anthropic.claude-sonnet-4")
                .temperature(0.7)
                .maxTokens(1000)
                .build();
    }

    /**
     * 设置系统提示词
     */
    public WorkerAgent withSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }
    /**
     * 设置描述
     */
    public WorkerAgent withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * 将异常映射为AgentException
     */
    private Throwable mapToAgentException(Throwable throwable) {
        if (throwable instanceof AgentException) {
            return throwable;
        }

        String message = throwable.getMessage();

        // 根据异常类型映射到具体的错误码
        if (throwable instanceof java.net.SocketTimeoutException ||
            (message != null && message.contains("timeout"))) {
            return new AgentException(
                ErrorCode.LLM_TIMEOUT,
                "LLM timeout for WorkerAgent: " + name, throwable);
        }

        if (message != null && (message.contains("rate limit") ||
            message.contains("429"))) {
            return new AgentException(
                ErrorCode.LLM_RATE_LIMITED,
                "LLM rate limited for WorkerAgent: " + name, throwable);
        }

        if (throwable instanceof java.net.ConnectException ||
            (message != null && message.contains("connection"))) {
            return new AgentException(
                ErrorCode.NETWORK_ERROR,
                "Network error for WorkerAgent: " + name, throwable);
        }

        // 默认映射为LLM调用失败
        return new AgentException(
            ErrorCode.LLM_CALL_FAILED,
            "LLM call failed for WorkerAgent: " + name, throwable);
    }
}