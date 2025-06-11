package cn.apmen.jsagent.config;

import cn.apmen.jsagent.framework.conversation.impl.InMemoryConversationService;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.llm.LlmConfig;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.Function;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent框架配置类
 */
@Configuration
public class AgentConfig {


    @Value("${agent.llm.openai.api-key}")
    private String openaiApiKey;

    @Value("${agent.llm.openai.base-url}")
    private String openaiApiBaseUrl;

    /**
     * 配置OpenAI统一客户端
     */
    @Bean
    public OpenAIUnifiedChatClient openAIUnifiedChatClient() {
        return new OpenAIUnifiedChatClient(openaiApiBaseUrl, openaiApiKey);
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
                .model("anthropic.claude-sonnet-4")
                .temperature(0.7)
                .maxTokens(2000)
                .build();
    }

    /**
     * 配置工具定义列表
     */
    @Bean
    public List<Tool> toolDefinitions() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 计算器工具定义
        Map<String, Object> calculatorProperties = new HashMap<>();
        Map<String, Object> expressionProperty = new HashMap<>();
        expressionProperty.put("type", "string");
        expressionProperty.put("description", "要计算的数学表达式，例如：25+17, 100/4, 50*2");
        calculatorProperties.put("expression", expressionProperty);
        Map<String, Object> calculatorParameters = new HashMap<>();
        calculatorParameters.put("type", "object");
        calculatorParameters.put("properties", calculatorProperties);
        calculatorParameters.put("required", Arrays.asList("expression"));
        Function calculatorFunction = new Function();
        calculatorFunction.setName("calculator");
        calculatorFunction.setDescription("执行基本的数学运算，支持加减乘除");
        calculatorFunction.setParameters(calculatorParameters);
        Tool calculatorTool = new Tool();
        calculatorTool.setType("function");
        calculatorTool.setFunction(calculatorFunction);
        // 天气查询工具定义
        Map<String, Object> weatherProperties = new HashMap<>();
        Map<String, Object> cityProperty = new HashMap<>();
        cityProperty.put("type", "string");
        cityProperty.put("description", "要查询天气的城市名称，例如：北京、上海、广州");
        weatherProperties.put("city", cityProperty);
        Map<String, Object> weatherParameters = new HashMap<>();
        weatherParameters.put("type", "object");
        weatherParameters.put("properties", weatherProperties);
        weatherParameters.put("required", Arrays.asList("city"));
        Function weatherFunction = new Function();
        weatherFunction.setName("weather_query");
        weatherFunction.setDescription("查询指定城市的天气情况");
        weatherFunction.setParameters(weatherParameters);
        Tool weatherTool = new Tool();
        weatherTool.setType("function");
        weatherTool.setFunction(weatherFunction);
        return Arrays.asList(calculatorTool, weatherTool);
    }

    /**
     * 配置核心Agent
     */
    @Bean
    public CoreAgent coreAgent(OpenAIUnifiedChatClient openAIClient,
                              ToolRegistry toolRegistry,
                              LlmConfig llmConfig,
                              List<Tool> toolDefinitions) {
        return CoreAgent.builder()
                .id("main-agent")
                .name("主要助手")
                .description("一个智能助手，可以帮助用户解决各种问题，包括数学计算和天气查询")
                .openAIUnifiedChatClient(openAIClient)
                .toolRegistry(toolRegistry)
                .llmConfig(llmConfig)
                .tools(toolDefinitions)
                .build();
    }
}