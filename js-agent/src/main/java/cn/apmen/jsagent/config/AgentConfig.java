package cn.apmen.jsagent.config;

import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.llm.LlmConfig;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.conversation.InMemoryConversationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Agent框架配置类
 */
@Configuration
public class AgentConfig {

    /**
     * 配置WebClient
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 配置OpenAI统一客户端
     */
    @Bean
    public OpenAIUnifiedChatClient openAIUnifiedChatClient(WebClient webClient) {
        return OpenAIUnifiedChatClient.builder()
                .webClient(webClient)
                .baseUrl("https://api.openai.com/v1")
                .apiKey("your-api-key-here") // 实际使用时应该从配置文件读取
                .build();
    }

    /**
     * 配置工具注册器
     */
    @Bean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    /**
     * 配置对话服务
     */
    @Bean
    public ConversationService conversationService() {
        return new InMemoryConversationService();
    }

    /**
     * 配置默认LLM配置
     */
    @Bean
    public LlmConfig defaultLlmConfig() {
        return LlmConfig.builder()
                .model("gpt-3.5-turbo")
                .temperature(0.7)
                .maxTokens(2000)
                .build();
    }

    /**
     * 配置核心Agent
     */
    @Bean
    public CoreAgent coreAgent(OpenAIUnifiedChatClient openAIClient, 
                              ToolRegistry toolRegistry,
                              LlmConfig llmConfig) {
        return CoreAgent.builder()
                .id("main-agent")
                .name("主要助手")
                .description("一个智能助手，可以帮助用户解决各种问题")
                .openAIUnifiedChatClient(openAIClient)
                .toolRegistry(toolRegistry)
                .llmConfig(llmConfig)
                .build();
    }
}