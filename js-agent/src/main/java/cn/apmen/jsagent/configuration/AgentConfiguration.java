package cn.apmen.jsagent.configuration;

import cn.apmen.jsagent.framework.agent.WorkerAgent;
import cn.apmen.jsagent.framework.conversation.impl.InMemoryConversationService;
import cn.apmen.jsagent.framework.core.AgentConfig;
import cn.apmen.jsagent.framework.core.AgentRunner;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.llm.LlmConfig;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import cn.apmen.jsagent.framework.openaiunified.model.request.Function;
import cn.apmen.jsagent.framework.tool.AgentTool;
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
public class AgentConfiguration {


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
     * 创建数学专家WorkerAgent
     */
    @Bean
    public WorkerAgent mathExpertAgent(OpenAIUnifiedChatClient openAIClient) {
        return WorkerAgent.builder()
                .id("math-expert")
                .name("数学专家")
                .systemPrompt("你是一个数学专家，专门解决复杂的数学问题。你会详细解释解题过程，并提供准确的答案。")
                .llmClient(openAIClient)
                .build();
    }

    /**
     * 创建写作助手WorkerAgent
     */
    @Bean
    public WorkerAgent writingAssistantAgent(OpenAIUnifiedChatClient openAIClient) {
        return WorkerAgent.builder()
                .id("writing-assistant")
                .name("写作助手")
                .systemPrompt("你是一个专业的写作助手，擅长各种文体的写作，包括文章、邮件、报告等。你会根据用户需求提供高质量的写作内容。")
                .llmClient(openAIClient)
                .build();
    }

    /**
     * 创建数学专家AgentTool - 直接输出给用户
     */
    @Bean
    public AgentTool mathExpertAgentTool(WorkerAgent mathExpertAgent) {
        // 定义Tool结构
        Tool toolDefinition = new Tool();
        toolDefinition.setType("function");

        Function function = new Function();
        function.setName("call_math_expert");
        function.setDescription("调用数学专家来解决复杂的数学问题，会直接向用户提供详细的解答");

        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> problemParam = new HashMap<>();
        problemParam.put("type", "string");
        problemParam.put("description", "需要解决的数学问题，可以是计算题、证明题、应用题等");
        properties.put("problem", problemParam);

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("problem"));

        function.setParameters(parameters);
        toolDefinition.setFunction(function);

        // 创建AgentTool，设置directOutput=true，直接输出给用户
        return new AgentTool(toolDefinition, mathExpertAgent, true);
    }

    /**
     * 创建写作助手AgentTool - 不直接输出，返回给主Agent
     */
    @Bean
    public AgentTool writingAssistantAgentTool(WorkerAgent writingAssistantAgent) {
        // 定义Tool结构
        Tool toolDefinition = new Tool();
        toolDefinition.setType("function");

        Function function = new Function();
        function.setName("call_writing_assistant");
        function.setDescription("调用写作助手来帮助创作文本内容，结果会返回给主助手进行进一步处理");

        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("type", "string");
        taskParam.put("description", "写作任务描述，例如：写一封邮件、写一篇文章、润色文本等");
        properties.put("task", taskParam);

        Map<String, Object> contentParam = new HashMap<>();
        contentParam.put("type", "string");
        contentParam.put("description", "相关的内容或要求");
        properties.put("content", contentParam);

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("task", "content"));

        function.setParameters(parameters);
        toolDefinition.setFunction(function);

        // 创建AgentTool，设置directOutput=false，不直接输出给用户
        return new AgentTool(toolDefinition, writingAssistantAgent, false);
    }

    /**
     * 配置工具定义列表
     */
    @Bean
    public List<Tool> toolDefinitions(AgentTool mathExpertAgentTool, AgentTool writingAssistantAgentTool) {
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

        // 为AgentTool创建纯净的Tool定义（不包含额外字段）
        Tool mathExpertToolDef = new Tool();
        mathExpertToolDef.setType(mathExpertAgentTool.getType());
        mathExpertToolDef.setFunction(mathExpertAgentTool.getFunction());
        
        Tool writingAssistantToolDef = new Tool();
        writingAssistantToolDef.setType(writingAssistantAgentTool.getType());
        writingAssistantToolDef.setFunction(writingAssistantAgentTool.getFunction());

        // 返回纯净的工具定义列表
        return Arrays.asList(
            calculatorTool,
            weatherTool,
            mathExpertToolDef,        // 数学专家工具定义（纯净版）
            writingAssistantToolDef   // 写作助手工具定义（纯净版）
        );
    }

    /**
     * 配置核心Agent
     */
    @Bean
    public CoreAgent coreAgent(OpenAIUnifiedChatClient openAIClient,
                              ToolRegistry toolRegistry,
                              LlmConfig llmConfig,
                              List<Tool> toolDefinitions,
                              AgentTool mathExpertAgentTool,
                              AgentTool writingAssistantAgentTool) {

        // 将AgentTool注册到ToolRegistry
        toolRegistry.registerExecutor(mathExpertAgentTool);
        toolRegistry.registerExecutor(writingAssistantAgentTool);
        return CoreAgent.builder()
                .id("main-agent")
                .name("主要助手")
                .description("一个智能助手，可以帮助用户解决各种问题，包括数学计算、天气查询，还可以调用专业的数学专家和写作助手")
                .openAIUnifiedChatClient(openAIClient)
                .toolRegistry(toolRegistry)
                .llmConfig(llmConfig)
                .tools(toolDefinitions)
                .build();
    }

    @Bean
    public AgentConfig agentConfig() {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setStreamToolCallContent(true);
        return agentConfig;
    }

    @Bean
    public AgentRunner agentRunner(CoreAgent coreAgent, AgentConfig agentConfig, ConversationService conversationService) {
        return new AgentRunner(coreAgent, agentConfig, conversationService);
    }

}