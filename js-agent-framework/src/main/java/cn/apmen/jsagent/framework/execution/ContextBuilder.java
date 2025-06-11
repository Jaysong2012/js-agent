package cn.apmen.jsagent.framework.execution;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.core.AgentConfig;
import cn.apmen.jsagent.framework.core.RunnerContext;
import cn.apmen.jsagent.framework.exception.AgentException;
import cn.apmen.jsagent.framework.exception.ErrorCode;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 上下文构建器
 * 专门负责构建和验证RunnerContext，分离职责
 */
@Slf4j
public class ContextBuilder {
    private final ConversationService conversationService;
    public ContextBuilder(ConversationService conversationService) {
        this.conversationService = conversationService;
    }
    /**
     * 构建RunnerContext
     */
    public RunnerContext buildContext(UserChatRequest request, String agentId, String executionId) {
        try {
            // 验证请求参数
            validateRequest(request);
            // 构建执行上下文
            ExecutionContext executionContext = ExecutionContext.builder()
                    .executionId(executionId)
                    .agentId(agentId)
                    .userId(request.getUserId())
                    .conversationId(request.getConversationId())
                    .build();
            // 构建RunnerContext
            RunnerContext.RunnerContextBuilder builder = RunnerContext.builder()
                    .conversationService(conversationService)
                    .userId(request.getUserId())
                    .conversationId(request.getConversationId())
                    .systemPrompt(buildSystemPrompt())
                    .executionContext(executionContext);
            // 添加用户消息到本地历史
            List<Message> messageHistory = Collections.synchronizedList(new ArrayList<>());
            if (request.getMessage() != null && request.getMessage().getMessage() != null) {
                messageHistory.add(new Message("user", request.getMessage().getMessage()));
            }
            builder.localMessageHistory(messageHistory);
            RunnerContext context = builder.build();
            // 验证构建的上下文
            validateContext(context);

            log.debug("Built context for execution {}, user {}, conversation {}",
                executionId, request.getUserId(), request.getConversationId());
            return context;
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentException(
                ErrorCode.CONTEXT_BUILD_FAILED,
                "Failed to build context for user: " + (request != null ? request.getUserId() : "null"), e);
        }
    }
    /**
     * 构建带配置的RunnerContext
     */
    public RunnerContext buildContext(UserChatRequest request, String agentId, String executionId, AgentConfig config) {
        RunnerContext context = buildContext(request, agentId, executionId);
        // 应用Agent配置
        if (config != null) {
            if (config.getMaxContextTokens() != null) {
                context.setMaxContextTokens(config.getMaxContextTokens());
            }
            if (config.getMaxRounds() != null) {
                context.setMaxRounds(config.getMaxRounds());
            }
        }
        return context;
    }
    /**
     * 验证请求参数
     */
    private void validateRequest(UserChatRequest request) {
        if (request == null) {
            throw new AgentException(ErrorCode.CONFIG_INVALID,
                "UserChatRequest cannot be null");
        }
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new AgentException(ErrorCode.CONFIG_INVALID,
                "User ID cannot be null or empty");
        }
        if (request.getConversationId() == null || request.getConversationId().trim().isEmpty()) {
            throw new AgentException(ErrorCode.CONFIG_INVALID,
                "Conversation ID cannot be null or empty");
        }

        if (request.getMessage() == null ||
            request.getMessage().getMessage() == null ||
            request.getMessage().getMessage().trim().isEmpty()) {
            throw new AgentException(ErrorCode.CONFIG_INVALID,
                "Message cannot be null or empty");
        }
    }
    /**
     * 验证构建的上下文
     */
    private void validateContext(RunnerContext context) {
        if (!context.isValid()) {
            throw new AgentException(
                ErrorCode.CONTEXT_INVALID_STATE,
                "Built context is invalid");
        }
    }
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        // 可以根据需要自定义系统提示词
        return "You are a helpful AI assistant.";
    }
}