package cn.apmen.jsagent.example.configuration;

import cn.apmen.jsagent.framework.agent.WorkerAgent;
import cn.apmen.jsagent.framework.conversation.ConversationService;
import cn.apmen.jsagent.framework.conversation.impl.InMemoryConversationService;
import cn.apmen.jsagent.framework.core.AgentConfig;
import cn.apmen.jsagent.framework.core.AgentRunner;
import cn.apmen.jsagent.framework.core.CoreAgent;
import cn.apmen.jsagent.framework.llm.LlmConfig;
import cn.apmen.jsagent.framework.mcp.MCPTool;
import cn.apmen.jsagent.framework.openaiunified.OpenAIUnifiedChatClient;
import cn.apmen.jsagent.framework.tool.AgentTool;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import cn.apmen.jsagent.example.tools.CalculatorTool;
import cn.apmen.jsagent.example.tools.WeatherTool;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent框架配置类 - 使用新的工具设计模式
 */
@Slf4j
@Configuration
public class AgentConfiguration {

    @Value("${agent.llm.openai.api-key}")
    private String openaiApiKey;

    @Value("${agent.llm.openai.base-url}")
    private String openaiApiBaseUrl;

    @Value("${agent.mcp.bingsearch.url}")
    private String bingSearchUrl;
    @Value("${agent.mcp.bingsearch.sse-endpoint}")
    private String bingSearchSseEndpoint;

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
        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> problemParam = new HashMap<>();
        problemParam.put("type", "string");
        problemParam.put("description", "需要解决的数学问题，可以是计算题、证明题、应用题等");
        properties.put("problem", problemParam);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"problem"});

        // 创建AgentTool，设置directOutput=true，直接输出给用户
        return new AgentTool(
            "call_math_expert",
            "调用数学专家来解决复杂的数学问题，会直接向用户提供详细的解答",
            parameters,
            new String[]{"problem"},
            mathExpertAgent,
            true
        );
    }

    /**
     * 创建写作助手AgentTool - 不直接输出，返回给主Agent
     */
    @Bean
    public AgentTool writingAssistantAgentTool(WorkerAgent writingAssistantAgent) {
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
        parameters.put("required", new String[]{"task", "content"});

        // 创建AgentTool，设置directOutput=false，不直接输出给用户
        return new AgentTool(
            "call_writing_assistant",
            "调用写作助手来帮助创作文本内容，结果会返回给主助手进行进一步处理",
            parameters,
            new String[]{"task", "content"},
            writingAssistantAgent,
            false
        );
    }

    /**
     * 创建Bing搜索MCP客户端
     */
    @Bean(name = "bingSearchMCPClient")
    public McpSyncClient bingSearchMCPClient() {
        try {
            log.info("正在初始化Bing搜索MCP客户端...");

            McpSyncClient client = McpClient.sync(
                            HttpClientSseClientTransport.builder(bingSearchUrl)
                                    .sseEndpoint(bingSearchSseEndpoint)
                                    .build())
                    .requestTimeout(Duration.ofSeconds(60))  // 增加请求超时时间
                    .initializationTimeout(Duration.ofSeconds(30))  // 增加初始化超时时间
                    .build();

            client.initialize();
            log.info("Bing搜索MCP客户端初始化成功");

            // 关闭时销毁资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    client.close();
                    log.info("Bing搜索MCP客户端已关闭");
                } catch (Exception e) {
                    log.error("关闭Bing搜索MCP客户端时发生错误", e);
                }
            }));

            return client;
        } catch (Exception e) {
            log.error("Bing搜索MCP客户端初始化失败", e);
            throw new RuntimeException("Failed to initialize Bing Search MCP client: " + e.getMessage(), e);
        }
    }

    /**
     * 创建Bing搜索MCPTool - 直接输出给用户
     */
    @Bean
    public MCPTool bingSearchMCPTool(McpSyncClient bingSearchMCPClient) {
        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // query参数
        Map<String, Object> queryParam = new HashMap<>();
        queryParam.put("type", "string");
        queryParam.put("description", "搜索输入");
        properties.put("query", queryParam);

        // top_k参数
        Map<String, Object> topKParam = new HashMap<>();
        topKParam.put("type", "integer");
        topKParam.put("description", "返回的结果数量，最多是10条，默认是3条");
        topKParam.put("default", 3);
        topKParam.put("minimum", 1);
        topKParam.put("maximum", 10);
        properties.put("top_k", topKParam);

        // is_fast参数
        Map<String, Object> isFastParam = new HashMap<>();
        isFastParam.put("type", "boolean");
        isFastParam.put("description", "是否快速模式。快速模式直接返回摘要，非快速可以获取完整网页内容，默认为true");
        isFastParam.put("default", true);
        properties.put("is_fast", isFastParam);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"query", "top_k", "is_fast"});

        // 创建MCPTool，设置directOutput=true，直接输出给用户
        return new MCPTool(
                "bing_search",
                "Bing网页搜索接口，返回json数据。资源包含网页的url和摘要，不包含完整信息。如果需要完整的信息，is_fast参数应该设置为false。",
                parameters,
                new String[]{"query", "top_k", "is_fast"},
                bingSearchMCPClient,
                "bing_search",
                true
        );
    }

    /**
     * 初始化工具注册
     */
    @Bean
    public String initializeTools(ToolRegistry toolRegistry,
                                 CalculatorTool calculatorTool,
                                 WeatherTool weatherTool,
                                 AgentTool mathExpertAgentTool,
                                 AgentTool writingAssistantAgentTool, MCPTool bingSearchMCPTool) {

        log.info("开始注册工具到ToolRegistry...");

        // 注册基础工具
        toolRegistry.registerExecutor(calculatorTool);
        toolRegistry.registerExecutor(weatherTool);

        // 注册Agent工具
        toolRegistry.registerExecutor(mathExpertAgentTool);
        toolRegistry.registerExecutor(writingAssistantAgentTool);

        // 注册MCP工具
        toolRegistry.registerExecutor(bingSearchMCPTool);

        log.info("工具注册完成，共注册 {} 个工具", toolRegistry.getToolCount());

        // 打印工具统计信息
        var stats = toolRegistry.getStatistics();
        log.info("工具统计信息: {}", stats);

        return "tools-initialized";
    }

    /**
     * 配置核心Agent
     */
    @Bean
    public CoreAgent coreAgent(OpenAIUnifiedChatClient openAIClient,
                              ToolRegistry toolRegistry,
                              LlmConfig llmConfig,
                              String initializeTools) { // 依赖工具初始化完成

        return CoreAgent.builder()
                .id("main-agent")
                .name("主要助手")
                .description("一个智能助手，可以帮助用户解决各种问题，包括数学计算、天气查询、还可以调用专业的数学专家和写作助手")
                .openAIUnifiedChatClient(openAIClient)
                .toolRegistry(toolRegistry)
                .llmConfig(llmConfig)
                .tools(toolRegistry.getAllTools()) // 直接从ToolRegistry获取所有工具
                .build();
    }

    /**
     * 配置Agent配置
     */
    @Bean
    public AgentConfig agentConfig() {
        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setStreamToolCallContent(true);
        return agentConfig;
    }

    /**
     * 配置Agent运行器
     */
    @Bean
    public AgentRunner agentRunner(CoreAgent coreAgent,
                                  AgentConfig agentConfig,
                                  ConversationService conversationService) {
        return new AgentRunner(coreAgent, agentConfig, conversationService);
    }
}
