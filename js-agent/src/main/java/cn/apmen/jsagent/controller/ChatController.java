package cn.apmen.jsagent.controller;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.core.AgentConfig;
import cn.apmen.jsagent.framework.core.AgentRunner;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.protocol.UserChatMessage;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 聊天控制器 - 提供Agent聊天API
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    @Autowired
    private CoreAgent coreAgent;

    @Autowired
    private ConversationService conversationService;

    /**
     * 非流式聊天接口
     */
    @PostMapping("/message")
    public Mono<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("收到聊天请求: {}", request.getMessage());

        String conversationId = request.getConversationId() != null ? request.getConversationId() : "default-conversation";
        // 构建用户聊天请求
        UserChatRequest userChatRequest = UserChatRequest.builder()
                .userId(request.getUserId() != null ? request.getUserId() : "default-user")
                .conversationId(conversationId)
                .message(new UserChatMessage(request.getMessage()))
                .build();

        conversationService.addMessage(conversationId, new Message("user", request.getMessage())).block();

        // 创建AgentRunner并执行
        AgentConfig agentConfig = new AgentConfig(); // 使用默认配置
        AgentRunner agentRunner = new AgentRunner(coreAgent, agentConfig, conversationService);
        return agentRunner.run(userChatRequest)
                .map(response -> ChatResponse.builder()
                        .success(true)
                        .message(response.getContent())
                        .conversationId(userChatRequest.getConversationId())
                        .build())
                .onErrorReturn(ChatResponse.builder()
                        .success(false)
                        .message("处理请求时发生错误")
                        .build());
    }

    /**
     * 流式聊天接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("收到流式聊天请求: {}", request.getMessage());

        String conversationId = request.getConversationId() != null ? request.getConversationId() : "default-conversation";
        // 构建用户聊天请求
        UserChatRequest userChatRequest = UserChatRequest.builder()
                .userId(request.getUserId() != null ? request.getUserId() : "default-user")
                .conversationId(conversationId)
                .message(new UserChatMessage(request.getMessage()))
                .build();

        conversationService.addMessage(conversationId, new Message("user", request.getMessage())).block();

        // 创建AgentRunner并执行流式处理
        AgentConfig agentConfig = new AgentConfig(); // 使用默认配置
        agentConfig.setStreamToolCallContent(true);
        AgentRunner agentRunner = new AgentRunner(coreAgent, agentConfig, conversationService);
        return agentRunner.runStream(userChatRequest)
                .filter(event -> event != null) // 过滤null事件
                .flatMap(event -> {
                    // 根据事件类型处理不同的响应
                    log.debug("Processing event: type={}, content={}", event.getType(),
                        event.getContent() != null ? event.getContent().substring(0, Math.min(50, event.getContent().length())) + "..." : "null");

                    switch (event.getType()) {
                        case TEXT_RESPONSE:
                            if (event.getContent() != null && !event.getContent().trim().isEmpty()) {
                                log.debug("Outputting TEXT_RESPONSE content: {} chars", event.getContent().length());
                                return Flux.just("" + event.getContent() + "\n\n");
                            }
                            log.debug("TEXT_RESPONSE content is null or empty");
                            break;
                        case THINKING:
                            if (event.getContent() != null && !event.getContent().trim().isEmpty()) {
                                return Flux.just("[THINKING] " + event.getContent() + "\n\n");
                            }
                            break;
                        case TOOL_CALL:
                            // 工具调用事件，输出工具调用信息给用户
                            if (event.getToolCalls() != null && !event.getToolCalls().isEmpty()) {
                                StringBuilder toolCallInfo = new StringBuilder("[TOOL_CALL] ");
                                for (var toolCall : event.getToolCalls()) {
                                    if (toolCall.getFunction() != null) {
                                        toolCallInfo.append(toolCall.getFunction().getName())
                                                   .append("(")
                                                   .append(toolCall.getFunction().getArguments())
                                                   .append(") ");
                                    }
                                }
                                return Flux.just("data: " + toolCallInfo.toString() + "\n\n");
                            }
                            log.debug("Tool call event received");
                            break;
                        case ERROR:
                            if (event.getError() != null) {
                                return Flux.just("[ERROR] " + event.getError() + "\n\n");
                            }
                            break;
                    }
                    return Flux.empty(); // 对于不需要发送的事件，返回空流
                })
                .switchIfEmpty(Flux.just("[NO_CONTENT]\n\n")) // 如果没有任何内容，发送占位符
                .concatWith(Flux.just("[DONE]\n\n")) // 添加结束标记
                .doOnSubscribe(subscription -> log.debug("Starting stream for conversation: {}", conversationId))
                .doOnNext(data -> log.trace("Streaming {}", data.replace("\n", "\\n")))
                .doOnComplete(() -> log.debug("Stream completed for conversation: {}", conversationId))
                .doOnError(error -> log.error("Stream error for conversation {}: {}", conversationId, error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Unhandled stream error: {}", error.getMessage(), error);
                    return Flux.just("[ERROR] 系统内部错误，请稍后重试\n\n", "[DONE]\n\n");
                });
    }

    /**
     * 聊天请求DTO
     */
    public static class ChatRequest {
        private String userId;
        private String conversationId;
        private String message;

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * 聊天响应DTO
     */
    public static class ChatResponse {
        private boolean success;
        private String message;
        private String conversationId;

        public static ChatResponseBuilder builder() {
            return new ChatResponseBuilder();
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }

        public static class ChatResponseBuilder {
            private boolean success;
            private String message;
            private String conversationId;

            public ChatResponseBuilder success(boolean success) {
                this.success = success;
                return this;
            }

            public ChatResponseBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ChatResponseBuilder conversationId(String conversationId) {
                this.conversationId = conversationId;
                return this;
            }

            public ChatResponse build() {
                ChatResponse response = new ChatResponse();
                response.setSuccess(this.success);
                response.setMessage(this.message);
                response.setConversationId(this.conversationId);
                return response;
            }
        }
    }
}