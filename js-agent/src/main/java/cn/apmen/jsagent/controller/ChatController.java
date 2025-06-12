package cn.apmen.jsagent.controller;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.core.AgentEvent;
import cn.apmen.jsagent.framework.core.AgentRunner;
import cn.apmen.jsagent.framework.protocol.UserChatMessage;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class ChatController {

    private final AgentRunner agentRunner;

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
    public Flux<AgentEvent> chatStream(@RequestBody ChatRequest request) {
        log.info("收到流式聊天请求: {}", request.getMessage());

        String conversationId = request.getConversationId() != null ? request.getConversationId() : "default-conversation";
        // 构建用户聊天请求
        UserChatRequest userChatRequest = UserChatRequest.builder()
                .userId(request.getUserId() != null ? request.getUserId() : "default-user")
                .conversationId(conversationId)
                .message(new UserChatMessage(request.getMessage()))
                .build();

        // 创建AgentRunner并执行流式处理
        return agentRunner.runStream(userChatRequest)
                .filter(event -> event != null) // 过滤null事件
                .doOnSubscribe(subscription -> log.debug("Starting stream for conversation: {}", conversationId))
                .doOnNext(event -> log.debug("Streaming event: type={}, content={}",
                    event.getType(),
                    event.getContent() != null ? event.getContent().substring(0, Math.min(50, event.getContent().length())) + "..." : "null"))
                .doOnComplete(() -> log.debug("Stream completed for conversation: {}", conversationId))
                .doOnError(error -> log.error("Stream error for conversation {}: {}", conversationId, error.getMessage()));
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