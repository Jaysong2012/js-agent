# JS Agent Framework

[![Maven Central](https://img.shields.io/maven-central/v/cn.apmen/js-agent-framework.svg)](https://search.maven.org/artifact/cn.apmen/js-agent-framework)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

一个基于 Spring Boot 和 Reactor 的企业级 Agent 框架，支持智能上下文管理、工具调用、流式响应和事件驱动架构。专为构建高性能、可扩展的 AI Agent 应用而设计。

## 🚀 核心特性

### 🤖 多 Agent 架构
- **CoreAgent**: 主控 Agent，负责对话管理和工具调用
- **WorkerAgent**: 专业 Agent，处理特定领域任务
- **AgentTool**: Agent 工具化，将 WorkerAgent 包装为可调用工具

### 🧠 智能上下文管理
- **上下文信息加载**: 自动加载用户信息、会话信息和环境信息
- **系统提示词模板**: 支持 Mustache 模板语法的动态系统提示词
- **个性化体验**: 根据用户偏好和会话历史定制 Agent 行为
- **环境感知**: 实时获取系统状态和可用工具信息

### 🔧 强大的工具系统
- **工具注册**: 动态注册和管理工具
- **流式工具**: 支持流式工具调用和响应
- **直接输出**: AgentTool 支持直接输出给用户或返回给主 Agent
- **工具上下文**: 丰富的工具执行上下文信息
- **并行执行**: 支持多工具并行调用，提升执行效率

### 📡 事件驱动架构
- **AgentEvent**: 统一的事件模型
    - `TEXT_RESPONSE`: 文本回复内容
    - `TOOL_CALL`: 工具调用事件
    - `TOOL_RESULT`: 工具执行结果
    - `DEBUG`: 系统调试信息
    - `ERROR`: 错误事件

### 🌊 流式处理
- **智能缓冲**: 根据配置智能决定缓冲策略
- **实时响应**: 支持 Server-Sent Events (SSE) 流式输出
- **背压控制**: 基于 Reactor 的响应式流处理

### 💾 双层存储架构
- **MemoryService**: 记录Agent运行中的所有事件和消息（assistant/tool/system等）
- **ConversationService**: 只记录用户可见的对话内容（user/TEXT_RESPONSE）
- **职责分离**: 完整记录 vs 用户体验，满足不同场景需求

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JS Agent Framework                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │ CoreAgent   │  │ AgentRunner │  │ RunnerContext       │                  │
│  │             │  │             │  │                     │                  │
│  │ - LLM调用   │  │ - 执行循环  │  │ - 消息管理          │                  │
│  │ - 工具调用  │  │ - 事件转换  │  │ - 轮次控制          │                  │
│  │ - 流式响应  │  │ - 异常处理  │  │ - 元数据存储        │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │MemoryService│  │ConversationS│  │ ContextInformation  │                  │
│  │             │  │ervice       │  │                     │                  │
│  │ - 完整记录  │  │ - 用户可见  │  │ - 用户信息          │                  │
│  │ - 所有消息  │  │ - 对话历史  │  │ - 会话信息          │                  │
│  │ - 上下文窗口│  │ - 会话管理  │  │ - 环境信息          │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │ ToolRegistry│  │ AgentTool   │  │ WorkerAgent         │                  │
│  │             │  │             │  │                     │                  │
│  │ - 工具注册  │  │ - Agent包装 │  │ - 专业领域          │                  │
│  │ - 并行执行  │  │ - 直接输出  │  │ - 独立LLM           │                  │
│  │ - 上下文传递│  │ - 流式支持  │  │ - 上下文感知        │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │StreamBuffer │  │ AgentEvent  │  │ SystemPrompt        │                  │
│  │             │  │             │  │                     │                  │
│  │ - 智能缓冲  │  │ - 事件模型  │  │ - Mustache模板      │                  │
│  │ - 流式控制  │  │ - TOOL_RESULT│ │ - 动态渲染          │                  │
│  │ - 背压处理  │  │ - 类型安全  │  │ - 个性化提示        │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 📦 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>cn.apmen</groupId>
    <artifactId>js-agent-framework</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

### 2. 基础配置

```java
@Configuration
public class AgentConfiguration {

    @Bean
    public OpenAIUnifiedChatClient openAIClient() {
        return new OpenAIUnifiedChatClient(
            "https://api.openai.com/v1", 
            "your-api-key"
        );
    }
    
    @Bean
    public CoreAgent coreAgent(OpenAIUnifiedChatClient client, ToolRegistry toolRegistry) {
        return CoreAgent.builder()
            .id("main-agent")
            .name("智能助手")
            .openAIUnifiedChatClient(client)
            .toolRegistry(toolRegistry)
            .build();
    }
    
    @Bean
    public AgentConfig agentConfig() {
        return AgentConfig.builder()
            .maxRounds(10)
            .maxContextTokens(4000)
            .streamToolCallContent(true)
            // 自定义系统提示词模板
            .systemPromptTemplate("""
                你是{{userInfo.username}}的专属助手。
                
                {{#userInfo}}
                用户偏好：
                - 语言：{{preferredLanguage}}
                - 时区：{{timezone}}
                - 回复风格：{{preferences.responseStyle}}
                {{/userInfo}}
                
                {{#conversationInfo}}
                {{^isNewConversation}}
                这是继续的对话，请保持上下文连贯性。
                {{/isNewConversation}}
                {{/conversationInfo}}
                
                {{#environmentInfo}}
                当前环境：
                - 时间：{{currentTime}}
                - 可用工具：{{#availableTools}}{{.}} {{/availableTools}}
                {{/environmentInfo}}
                
                请根据用户需求提供个性化的帮助。
                """)
            .build();
    }

    @Bean
    public MemoryService memoryService() {
        return new InMemoryMemoryService();
    }

    @Bean
    public ConversationService conversationService() {
        return new InMemoryConversationService();
    }
}
```

### 3. 创建 WorkerAgent

```java
@Bean
public WorkerAgent mathExpert(OpenAIUnifiedChatClient client) {
return WorkerAgent.builder()
.id("math-expert")
.name("数学专家")
.systemPrompt("你是一个数学专家，专门解决数学问题")
.llmClient(client)
.build();
}
```

### 4. 创建 AgentTool

```java
@Bean
public AgentTool mathTool(WorkerAgent mathExpert) {
// 定义工具结构
Tool toolDef = new Tool();
toolDef.setType("function");

    Function function = new Function();
    function.setName("solve_math");
    function.setDescription("解决数学问题");
    // ... 设置参数
    
    toolDef.setFunction(function);
    
    // 创建 AgentTool，directOutput=true 表示直接输出给用户
    return new AgentTool(toolDef, mathExpert, true);
}
```

### 5. 使用 AgentRunner

```java
@Service
public class ChatService {

    @Autowired
    private CoreAgent coreAgent;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private MemoryService memoryService;

    @Autowired
    private AgentConfig agentConfig;
    
    public Flux<AgentEvent> chat(String userId, String message) {
        UserChatRequest request = UserChatRequest.builder()
            .userId(userId)
            .conversationId("conv-" + userId)
            .message(new UserChatMessage(message))
            .build();
        
        AgentRunner runner = new AgentRunner(
            coreAgent, agentConfig, conversationService, memoryService
        );
        
        return runner.runStream(request);
    }
}
```

## 🧠 智能上下文管理详解

### 上下文信息加载

框架会自动加载三类上下文信息：

#### 1. 用户信息 (UserInformation)
```java
// 框架自动加载的用户信息结构
UserInformation userInfo = UserInformation.builder()
    .userId("user-123")
    .username("张三")
    .preferredLanguage("zh-CN")
    .timezone("Asia/Shanghai")
    .userLevel("VIP")
    .preferences(Map.of(
        "responseStyle", "professional",
        "detailLevel", "high"
    ))
    .build();
```

#### 2. 会话信息 (ConversationInformation)
```java
// 框架自动加载的会话信息
ConversationInformation convInfo = ConversationInformation.builder()
    .conversationId("conv-456")
    .isNewConversation(false)
    .messageCount(15)
    .lastActiveTime(LocalDateTime.now())
    .build();
```

#### 3. 环境信息 (EnvironmentInformation)
```java
// 框架自动加载的环境信息
EnvironmentInformation envInfo = EnvironmentInformation.builder()
    .currentTime(LocalDateTime.now())
    .systemVersion("1.0.0")
    .availableTools(List.of("calculator", "weather", "search"))
    .systemLoad("normal")
    .build();
```

### 系统提示词模板渲染

#### 1. 默认模板
框架提供了内置的默认模板：

```mustache
你是一个智能助手，可以帮助用户解决各种问题，包括数学计算和天气查询。

{{#userInfo}}
用户信息：
- 用户名：{{username}}
- 首选语言：{{preferredLanguage}}
- 时区：{{timezone}}
  {{#preferences.responseStyle}}
- 请使用{{.}}的回复风格
  {{/preferences.responseStyle}}
  {{/userInfo}}

{{#conversationInfo}}
{{^isNewConversation}}
这是一个继续的对话，请保持上下文的连贯性。
{{/isNewConversation}}
{{/conversationInfo}}

{{#environmentInfo}}
当前时间：{{currentTime}}
{{#availableTools}}
可用工具：{{#.}}{{.}}{{^-last}}, {{/-last}}{{/.}}
{{/availableTools}}
{{/environmentInfo}}

请根据用户的需求提供准确、有用的回答。
```

#### 2. 自定义模板
可以通过 AgentConfig 设置自定义模板：

```java
AgentConfig config = AgentConfig.builder()
.systemPromptTemplate("""
# {{userInfo.username}}的专属AI助手

        ## 用户档案
        {{#userInfo}}
        - 👤 用户：{{username}} ({{userLevel}})
        - 🌍 语言：{{preferredLanguage}}
        - ⏰ 时区：{{timezone}}
        - 🎨 风格：{{preferences.responseStyle}}
        {{/userInfo}}
        
        ## 会话状态
        {{#conversationInfo}}
        {{#isNewConversation}}
        🆕 这是一个新的对话
        {{/isNewConversation}}
        {{^isNewConversation}}
        🔄 继续之前的对话 ({{messageCount}}条消息)
        {{/isNewConversation}}
        {{/conversationInfo}}
        
        ## 系统环境
        {{#environmentInfo}}
        - 📅 当前时间：{{currentTime}}
        - 🛠️ 可用工具：{{#availableTools}}{{.}} {{/availableTools}}
        - 📊 系统负载：{{systemLoad}}
        {{/environmentInfo}}
        
        请提供个性化、专业的服务！
        """)
    .build();
```

#### 3. 模板语法支持

支持完整的 Mustache 语法：

```mustache
{{! 注释 }}

{{! 变量输出 }}
{{userInfo.username}}

{{! 条件判断 }}
{{#userInfo}}
用户存在时显示
{{/userInfo}}

{{! 反向条件 }}
{{^conversationInfo.isNewConversation}}
不是新对话时显示
{{/conversationInfo.isNewConversation}}

{{! 循环 }}
{{#environmentInfo.availableTools}}
- 工具：{{.}}
  {{/environmentInfo.availableTools}}

{{! 嵌套对象 }}
{{userInfo.preferences.responseStyle}}
```

### 双层存储架构设计

#### MemoryService vs ConversationService

**MemoryService 职责**：
- 记录Agent运行中的**所有**事件和消息
- 包含 system、user、assistant、tool 等所有类型消息
- 用于Agent推理和上下文管理
- 支持智能上下文窗口截取

```java
// MemoryService 记录所有消息
memoryService.addMessage(conversationId, new Message("system", "系统提示"));
memoryService.addMessage(conversationId, new Message("user", "用户消息"));
memoryService.addMessage(conversationId, new Message("assistant", "Agent思考"));
memoryService.addMessage(conversationId, new Message("tool", "工具结果"));

// 获取完整记录用于Agent推理
List<Message> allMessages = memoryService.getMemoryHistory(conversationId);
List<Message> contextWindow = memoryService.getContextMemory(conversationId, maxTokens, systemPrompt);
```

**ConversationService 职责**：
- 只记录**用户可见**的对话内容
- 包含用户输入和Agent的最终回复
- 用于对话历史展示和会话管理
- 提供清洁的用户体验

```java
// ConversationService 只记录用户可见内容
conversationService.addMessage(conversationId, new Message("user", "用户消息"));
conversationService.addMessage(conversationId, new Message("assistant", "最终回复"));

// 获取对话历史用于展示
List<Message> chatHistory = conversationService.getConversationHistory(conversationId);
```

#### 数据流设计

```
用户输入 → RunnerContext.addMessage()
    ├─ 记录到 MemoryService（所有消息）
    └─ 记录到 ConversationService（仅用户消息）

Agent处理 → 各种内部消息 → MemoryService（所有消息）

Agent完整回复完成 → recordCompleteResponseToConversation()
    ├─ 从 MemoryService 获取最新assistant消息
    ├─ 合并为完整回复
    └─ 记录到 ConversationService（完整回复）
```

## 🔧 核心组件详解

### AgentRunner - 执行引擎增强

AgentRunner 现在支持更智能的上下文管理：

```java
AgentRunner runner = new AgentRunner(coreAgent, config, conversationService);

// 执行流程：
// 1. 加载上下文信息 (loadContextInformation)
// 2. 丰富上下文数据 (enrichContextInformation)  
// 3. 构建系统提示词 (buildSystemPromptWithContext)
// 4. 创建运行上下文 (buildRunnerContextWithInfo)
// 5. 执行对话循环 (executeStreamLoop)

Flux<AgentEvent> events = runner.runStream(request);
```

**新增功能**：
- 自动加载用户、会话、环境信息
- 基于模板的系统提示词生成
- 智能的消息持久化策略
- 增强的错误处理和降级机制

### 事件系统

统一的事件模型，支持不同类型的 Agent 事件：

```java
// 文本响应事件
AgentEvent.textResponse("回复内容", true);

// 工具调用事件
AgentEvent.toolCall(toolCalls);

// 工具结果事件
AgentEvent.toolResult(toolResults);

// 调试事件
AgentEvent.debug("调试信息", DebugLevel.INFO);

// 错误事件
AgentEvent.error("错误信息");
```

## 🌊 流式处理

### StreamBuffer - 智能缓冲

根据配置自动决定缓冲策略：

```java
// 配置流式输出
AgentConfig config = new AgentConfig();
config.setStreamToolCallContent(true);  // 立即输出所有内容
config.setStreamToolCallContent(false); // 智能缓冲，根据工具调用情况决定
```

**缓冲策略**：
- `streamToolCallContent=true`: 所有内容立即流式输出
- `streamToolCallContent=false`: 智能缓冲，有工具调用时只输出工具结果

### 事件流处理

```java
runner.runStream(request)
.filter(event -> event.getType() == AgentEvent.EventType.TEXT_RESPONSE)
.subscribe(event -> {
// 处理文本响应事件
System.out.println(event.getContent());
});
```

## 🛠️ 工具开发

### 1. 实现 ToolExecutor

```java
@Component
public class CustomTool implements ToolExecutor {

    @Override
    public String getToolName() {
        return "custom_tool";
    }
    
    @Override
    public String getDescription() {
        return "自定义工具描述";
    }
    
    @Override
    public Mono<ToolResult> execute(ToolCall toolCall, ToolContext context) {
        // 工具逻辑实现
        return Mono.just(ToolResult.success(toolCall.getId(), "执行结果"));
    }
}
```

### 2. 支持流式工具

```java
@Component
public class StreamingTool implements StreamingToolExecutor {

    @Override
    public Flux<BaseToolResponse> executeStream(ToolCall toolCall, ToolContext context) {
        // 流式工具逻辑
        return Flux.range(1, 5)
            .map(i -> ToolResponse.success(toolCall.getId(), "步骤 " + i));
    }
}
```

### 3. 注册工具

```java
@Autowired
private ToolRegistry toolRegistry;

@PostConstruct
public void registerTools() {
toolRegistry.registerExecutor(customTool);
toolRegistry.registerExecutor(streamingTool);
}
```

## 💬 对话管理

### ConversationService

```java
@Service
public class CustomConversationService implements ConversationService {

    @Override
    public Mono<List<Message>> getMessageHistory(String conversationId) {
        // 从数据库获取历史消息
        return messageRepository.findByConversationId(conversationId);
    }
    
    @Override
    public Mono<Void> addMessage(String conversationId, Message message) {
        // 保存消息到数据库
        return messageRepository.save(conversationId, message);
    }
}
```

### 会话元数据

```java
ConversationMetadata metadata = ConversationMetadata.builder()
.conversationId("conv-123")
.userId("user-456")
.agentId("agent-789")
.title("数学问题讨论")
.status(ConversationStatus.ACTIVE)
.priority(ConversationPriority.HIGH)
.build();
```

## 🔍 调试和监控

### 调试事件

框架提供丰富的调试事件：

```java
// 不同级别的调试事件
AgentEvent.debugTrace("详细跟踪信息");
AgentEvent.debugInfo("一般信息");
AgentEvent.debugWarn("警告信息");
AgentEvent.debugError("错误信息");
```

### 日志配置

```yaml
logging:
level:
cn.apmen.jsagent.framework: DEBUG
cn.apmen.jsagent.framework.core.AgentRunner: TRACE
```

## 🚀 高级特性

### 1. 自定义 LLM 提供商

```java
public class CustomLLMClient implements OpenAIUnifiedChatClient {
// 实现自定义 LLM 调用逻辑
}
```

### 2. 插件系统

```java
@Component
public class CustomPlugin implements AgentPlugin {

    @Override
    public void beforeExecution(RunnerContext context) {
        // 执行前处理
    }
    
    @Override
    public void afterExecution(RunnerContext context, AgentEvent event) {
        // 执行后处理
    }
}
```

### 3. 生命周期管理

```java
@Component
public class AgentLifecycleHandler implements AgentLifecycle {

    @Override
    public void onStart(String agentId) {
        // Agent 启动时处理
    }
    
    @Override
    public void onStop(String agentId) {
        // Agent 停止时处理
    }
}
```

## 📚 最佳实践

### 1. Agent 设计原则
- **单一职责**: 每个 WorkerAgent 专注于特定领域
- **松耦合**: Agent 之间通过工具接口交互
- **状态管理**: 合理使用上下文和会话状态

### 2. 工具开发建议
- **幂等性**: 工具调用应该是幂等的
- **错误处理**: 优雅处理异常情况
- **性能优化**: 避免长时间阻塞操作

### 3. 流式处理优化
- **背压控制**: 合理控制流式数据的产生速度
- **缓冲策略**: 根据业务需求选择合适的缓冲策略
- **错误恢复**: 实现流式处理的错误恢复机制

### 4. 上下文管理最佳实践
- **用户信息缓存**: 合理缓存用户偏好，减少重复加载
- **模板复用**: 设计可复用的系统提示词模板
- **降级策略**: 确保在外部服务不可用时仍能正常工作
- **性能监控**: 监控上下文加载和模板渲染的性能

## 🔄 版本更新

### v0.0.2 新特性
- ✨ **智能上下文管理**: 自动加载用户、会话、环境信息
- 🎨 **系统提示词模板**: 支持 Mustache 语法的动态模板
- 🔧 **双层存储架构**: MemoryService 和 ConversationService 职责分离
- 📡 **完整事件系统**: 新增 TOOL_RESULT 事件，完善事件流
- 🚀 **性能提升**: 移除冗余的本地缓存，优化消息处理
- 🛡️ **容错增强**: 更好的降级机制和错误处理

### 迁移指南

从 v0.0.1 升级到 v0.0.2：

1. **更新依赖注入**：
   ```java
   // 新版本需要同时注入 MemoryService 和 ConversationService
   @Autowired
   private MemoryService memoryService;

   @Autowired
   private ConversationService conversationService;

   // AgentRunner 构造函数参数更新
   AgentRunner runner = new AgentRunner(
       coreAgent, agentConfig, conversationService, memoryService
   );
   ```

2. **配置 Bean**：
   ```java
   @Bean
   public MemoryService memoryService() {
       return new InMemoryMemoryService();
   }

   @Bean
   public ConversationService conversationService() {
       return new InMemoryConversationService();
   }
   ```

3. **事件处理更新**：
   ```java
   // 新版本支持完整的事件类型
   runner.runStream(request)
       .subscribe(event -> {
           switch (event.getType()) {
               case TEXT_RESPONSE:
                   // 处理文本回复
                   break;
               case TOOL_CALL:
                   // 处理工具调用
                   break;
               case TOOL_RESULT:
                   // 处理工具结果 (新增)
                   break;
               case DEBUG:
                   // 处理调试信息
                   break;
           }
       });
   ```

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 Apache License 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [Project Reactor](https://projectreactor.io/) - 响应式编程
- [OpenAI](https://openai.com/) - LLM 服务提供商
- [Mustache.java](https://github.com/spullara/mustache.java) - 模板引擎

---

**JS Agent Framework** - 让 Agent 开发更简单、更强大！ 🚀