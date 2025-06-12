package cn.apmen.jsagent.framework.core;

import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.llm.LlmConfig;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.openaiunified.model.request.ChatCompletionRequest;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.response.ChatCompletionResponse;
import cn.apmen.jsagent.framework.openaiunified.model.response.Choice;
import cn.apmen.jsagent.framework.openaiunified.model.response.stream.StreamChoice;
import cn.apmen.jsagent.framework.stream.SSEParser;
import cn.apmen.jsagent.framework.stream.StreamAccumulator;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Data
@Builder
@Slf4j
/**
 * 核心Agent, 从 RunnerContext 构建 ChatCompletionRequest, 调用 OpenAI 接口，并解析返回AgentResponse
 * 只负责单次LLM调用，不处理工具调用循环
 */
public class CoreAgent {

    private final String id;
    private final String name;
    private String description;

    private LlmConfig llmConfig;
    private List<Tool> tools;
    private OpenAIUnifiedChatClient openAIUnifiedChatClient;
    private ToolRegistry toolRegistry;

    @Builder.Default
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Builder.Default
    private final SSEParser sseParser = new SSEParser();

    /**
     * 流式运行Agent - 单次调用
     */
    public Flux<AgentResponse> runStream(RunnerContext runnerContext) {
        try {
            // 构建ChatCompletionRequest
            ChatCompletionRequest request = buildChatRequest(runnerContext);
            request.setStream(true); // 设置流式参数
            
            // 记录请求信息，但避免序列化整个request对象
            log.info("Starting stream request - model: {}, messages: {}, tools: {}", 
                request.getModel(), 
                request.getMessages() != null ? request.getMessages().size() : 0,
                request.getTools() != null ? request.getTools().size() : 0);

            // 调用OpenAI流式API，使用SSE解析器
            return openAIUnifiedChatClient.createChatCompletionStream(request)
                    .doOnSubscribe(subscription -> log.debug("Starting stream subscription for agent: {}", name))
                    .doOnNext(rawData -> log.trace("Raw stream data received: {}", rawData))
                    .doOnError(error -> log.error("Stream error in OpenAI client: {}", error.getMessage(), error))
                    .doOnComplete(() -> log.debug("OpenAI stream completed for agent: {}", name))
                    .transform(sseParser::parseSSEStream)
                    .doOnNext(streamResponse -> log.trace("Parsed stream response: {}", streamResponse))
                    .scan(new StreamAccumulator(), (accumulator, streamResponse) -> {
                        // 使用scan操作符来维护累积器状态
                        if (streamResponse.getChoices() != null && !streamResponse.getChoices().isEmpty()) {
                            StreamChoice choice = streamResponse.getChoices().get(0);
                            log.trace("Processing stream choice: finishReason={}, hasContent={}, hasToolCalls={}",
                                choice.getFinishReason(),
                                choice.getDelta() != null && choice.getDelta().getContent() != null,
                                choice.getDelta() != null && choice.getDelta().getToolCalls() != null);
                            accumulator.accumulate(choice);
                        }
                        return accumulator;
                    })
                    .doOnNext(accumulator -> log.trace("Accumulator state: hasNewContent={}, isComplete={}",
                        accumulator.hasNewContent(), accumulator.isComplete()))
                    .flatMap(accumulator -> {
                        // 处理内容片段
                        if (accumulator.hasNewContent()) {
                            String newContent = accumulator.getNewContent();
                            return Flux.just(AgentResponse.text(newContent, false));
                        }

                        // 处理完成事件
                        if (accumulator.isComplete()) {
                            Message completeMessage = accumulator.buildMessage();
                            // 添加完整消息到上下文
                            runnerContext.addMessage(completeMessage);

                            // 检查是否有工具调用
                            if (completeMessage.getToolCalls() != null && !completeMessage.getToolCalls().isEmpty()) {
                                return Flux.just(AgentResponse.toolCall(completeMessage.getToolCalls()));
                            } else {
                                // 最终文本响应
                                String content = completeMessage.getContent() != null ? completeMessage.getContent() : "";
                                return Flux.just(AgentResponse.text(content, true));
                            }
                        }

                        return Flux.empty();
                    })
                    .doOnNext(response -> log.debug("Agent response generated: type={}, final={}",
                        response.getType(), response.isFinalResponse()))
                    .onErrorMap(this::mapToAgentException);

        } catch (Exception e) {
            log.error("Error in agent stream execution", e);
            throw new AgentException(ErrorCode.AGENT_EXECUTION_FAILED,
                "Stream execution failed for agent: " + name, e);
        }
    }

    /**
     * 非流式运行Agent - 单次调用
     */
    public Mono<AgentResponse> run(RunnerContext runnerContext) {
        try {
            // 构建ChatCompletionRequest
            ChatCompletionRequest request = buildChatRequest(runnerContext);

            log.info("request: {}", objectMapper.writeValueAsString(request));

            // 调用OpenAI API
            return openAIUnifiedChatClient.createChatCompletion(request)
                    .map(response -> parseResponse(response, runnerContext))
                    .onErrorMap(this::mapToAgentException);

        } catch (Exception e) {
            log.error("Error in agent execution", e);
            throw new AgentException(ErrorCode.AGENT_EXECUTION_FAILED,
                "Execution failed for agent: " + name, e);
        }
    }

    /**
     * 构建ChatCompletionRequest
     */
    private ChatCompletionRequest buildChatRequest(RunnerContext runnerContext) {
        ChatCompletionRequest request = new ChatCompletionRequest();

        // 设置模型
        String model = (llmConfig != null && llmConfig.getModel() != null)
                ? llmConfig.getModel()
                : "anthropic.claude-sonnet-4";
        request.setModel(model);

        // 设置消息
        List<Message> messages = runnerContext.getCompleteMessageList();
        request.setMessages(messages);

        // 设置工具
        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
            request.setToolChoice("auto");
        }

        // 设置其他参数
        if (llmConfig != null) {
            request.setTemperature(llmConfig.getTemperature());
            request.setMaxTokens(llmConfig.getMaxTokens());
        }

        return request;
    }

    /**
     * 解析非流式响应
     */
    private AgentResponse parseResponse(ChatCompletionResponse response, RunnerContext runnerContext) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return AgentResponse.error("No response choices available");
        }

        Choice choice = response.getChoices().get(0);
        Message message = choice.getMessage();

        if (message == null) {
            return AgentResponse.error("No message in response");
        }

        // 更新上下文 - 添加助手的响应消息
        runnerContext.addMessage(message);

        // 检查是否有工具调用
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            return AgentResponse.toolCall(message.getToolCalls());
        }

        // 普通文本响应
        String content = message.getContent() != null ? message.getContent() : "";
        return AgentResponse.text(content, true);
    }

    /**
     * 获取工具注册器（供AgentRunner使用）
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
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
            return new AgentException(ErrorCode.LLM_TIMEOUT,
                "LLM call timeout for agent: " + name, throwable);
        }

        if (message != null && (message.contains("rate limit") ||
            message.contains("429"))) {
            return new AgentException(ErrorCode.LLM_RATE_LIMITED,
                "LLM rate limited for agent: " + name, throwable);
        }

        if (throwable instanceof java.net.ConnectException ||
            (message != null && message.contains("connection"))) {
            return new AgentException(ErrorCode.NETWORK_ERROR,
                "Network error for agent: " + name, throwable);
        }

        // 默认映射为LLM调用失败
        return new AgentException(ErrorCode.LLM_CALL_FAILED,
            "LLM call failed for agent: " + name, throwable);
    }
}