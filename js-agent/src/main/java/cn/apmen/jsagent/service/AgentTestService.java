package cn.apmen.jsagent.service;

import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.core.AgentConfig;
import cn.apmen.jsagent.framework.core.AgentRunner;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.openaiunified.model.request.Message;
import cn.apmen.jsagent.framework.protocol.UserChatMessage;
import cn.apmen.jsagent.framework.protocol.UserChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

/**
 * Agent测试服务 - 用于验证框架功能
 */
@Service
@Slf4j
public class AgentTestService implements CommandLineRunner {

    @Autowired
    private CoreAgent coreAgent;
    @Autowired
    private ConversationService conversationService;

    @Override
    public void run(String... args) throws Exception {
        // 等待一段时间确保所有组件都已初始化
        Thread.sleep(2000);
        log.info("开始Agent框架功能测试...");
        // 测试基本对话
        //testBasicChat();
        // 测试工具调用
        //testToolCalls();
        log.info("Agent框架功能测试完成");
    }

    /**
     * 测试基本对话功能
     */
    private void testBasicChat() {
        log.info("=== 测试基本对话功能 ===");
        try {
            UserChatRequest request = UserChatRequest.builder()
                    .userId("test-user")
                    .conversationId("test-conversation-1")
                    .message(new UserChatMessage("你好，请介绍一下你自己"))
                    .build();
            conversationService.addMessage("test-conversation-1", new Message("user", "请帮我计算 25 + 17 的结果")).block();

            AgentRunner agentRunner = new AgentRunner(coreAgent, new AgentConfig(), conversationService);
            agentRunner.run(request)
                    .doOnNext(response -> {
                        log.info("基本对话测试结果: {}", response.getContent());
                    })
                    .doOnError(error -> {
                        log.error("基本对话测试失败", error);
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("基本对话测试异常", e);
        }
    }

    /**
     * 测试工具调用功能
     */
    private void testToolCalls() {
        log.info("=== 测试工具调用功能 ===");
        // 测试计算器工具
        testCalculatorTool();
        // 测试天气查询工具
        testWeatherTool();
    }

    /**
     * 测试计算器工具
     */
    private void testCalculatorTool() {
        log.info("--- 测试计算器工具 ---");
        try {
            UserChatRequest request = UserChatRequest.builder()
                    .userId("test-user")
                    .conversationId("test-conversation-2")
                    .message(new UserChatMessage("请帮我计算 25 + 17 的结果"))
                    .build();
            conversationService.addMessage("test-conversation-2", new Message("user", "请帮我计算 25 + 17 的结果")).block();

            AgentRunner agentRunner = new AgentRunner(coreAgent, new AgentConfig(), conversationService);
            agentRunner.run(request)
                    .doOnNext(response -> {
                        log.info("计算器工具测试结果: {}", response.getContent());
                    })
                    .doOnError(error -> {
                        log.error("计算器工具测试失败", error);
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("计算器工具测试异常", e);
        }
    }

    /**
     * 测试天气查询工具
     */
    private void testWeatherTool() {
        log.info("--- 测试天气查询工具 ---");
        try {
            UserChatRequest request = UserChatRequest.builder()
                    .userId("test-user")
                    .conversationId("test-conversation-3")
                    .message(new UserChatMessage("请查询北京的天气情况"))
                    .build();
            conversationService.addMessage("test-conversation-3", new Message("user", "请帮我计算 25 + 17 的结果")).block();

            AgentRunner agentRunner = new AgentRunner(coreAgent, new AgentConfig(), conversationService);
            agentRunner.run(request)
                    .doOnNext(response -> {
                        log.info("天气查询工具测试结果: {}", response.getContent());
                    })
                    .doOnError(error -> {
                        log.error("天气查询工具测试失败", error);
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("天气查询工具测试异常", e);
        }
    }
}