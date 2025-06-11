package cn.apmen.jsagent.controller;

import cn.apmen.jsagent.framework.core.AgentRunner;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.core.AgentResponse;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
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
        
        // 构建用户聊天请求
        UserChatRequest userChatRequest = UserChatRequest.builder()
                .userId(request.getUserId() != null ? request.getUserId() : "default-user")
                .conversationId(request.getConversationId() != null ? request.getConversationId() : "default-conversation")
                .message(request.getMessage())
                .build();

        // 创建AgentRunner并执行
        AgentRunner agentRunner = new AgentRunner(coreAgent, conversationService);
        
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
        
        // 构建用户聊天请求
        UserChatRequest userChatRequest = UserChatRequest.builder()
                .userId(request.getUserId() != null ? request.getUserId() : "default-user")
                .conversationId(request.getConversationId() != null ? request.getConversationId() : "default-conversation")
                .message(request.getMessage())
                .build();

        // 创建AgentRunner并执行流式处理
        AgentRunner agentRunner = new AgentRunner(coreAgent, conversationService);
        
        return agentRunner.runStream(userChatRequest)
                .map(response -> {
                    if (response.isComplete()) {
                        return "data: " + response.getContent() + "\n\n";
                    } else {
                        return "data: " + response.getContent() + "\n\n";
                    }
                })
                .onErrorReturn("data: [ERROR] 处理请求时发生错误\n\n");
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