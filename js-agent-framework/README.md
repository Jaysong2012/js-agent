# JS Agent Framework

[![Maven Central](https://img.shields.io/maven-central/v/cn.apmen/js-agent-framework.svg)](https://search.maven.org/artifact/cn.apmen/js-agent-framework)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java Version](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

一个基于 Spring Boot 和 Reactor 的企业级 Agent 框架，支持多 Agent 协作、工具调用、流式响应和事件驱动架构。

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

### 📡 事件驱动架构
- **AgentEvent**: 统一的事件模型
  - `TEXT_RESPONSE`: 用户回复内容
  - `TOOL_CALL`: 工具调用事件
  - `TOOL_RESULT`: 工具执行结果
  - `DEBUG`: 系统调试信息
  - `ERROR`: 错误事件

### 🌊 流式处理
- **智能缓冲**: 根据配置智能决定缓冲策略
- **实时响应**: 支持 Server-Sent Events (SSE) 流式输出
- **背压控制**: 基于 Reactor 的响应式流处理

### 💬 对话管理
- **会话持久化**: 支持内存和外部存储
- **上下文管理**: 自动管理对话历史和 Token 限制
- **多轮对话**: 支持复杂的多轮对话场景
- **职责分离**: RunnerContext 负责执行期间缓存，ConversationService 负责持久化

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           JS Agent Framework                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │ CoreAgent   │  │ AgentRunner │  │ ConversationService │                  │
│  │             │  │             │  │                     │                  │
│  │ - LLM调用   │  │ - 执行循环  │  │ - 对话历史          │                  │
│  │ - 工具调用  │  │ - 事件转换  │  │ - 会话管理          │                  │
│  │ - 流式响应  │  │ - 异常处理  │  │ - 上下文维护        │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │ContextInfo  │  │ SystemPrompt│  │ TemplateEngine      │                  │
│  │             │  │             │  │                     │                  │
│  │ - 用户信息  │  │ - 模板渲染  │  │ - Mustache支持      │                  │
│  │ - 会话信息  │  │ - 动态生成  │  │ - 数据绑定          │                  │
│  │ - 环境信息  │  │ - 个性化    │  │ - 降级处理          │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │ ToolRegistry│  │ AgentTool   │  │ WorkerAgent         │                  │
│  │             │  │             │  │                     │                  │
│  │ - 工具注册  │  │ - Agent包装 │  │ - 专业领域          │                  │
│  │ - 执行管理  │  │ - 直接输出  │  │ - 独立LLM           │                  │
│  │ - 上下文传递│  │ - 流式支持  │  │ - 上下文感知        │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐                  │
│  │StreamBuffer │  │ AgentEvent  │  │ OpenAIUnified       │                  │
│  │             │  │             │  │                     │                  │
│  │ - 智能缓冲  │  │ - 事件模型  │  │ - LLM统一接口       │                  │
│  │ - 流式控制  │  │ - 类型安全  │  │ - 多提供商支持      │                  │
│  │ - 背压处理  │  │ - 序列化    │  │ - 流式调用          │                  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 📦 快速开始

### 1. 添加依赖

```xml
<dependency>
<groupId>cn.apmen</groupId>
<artifactId>js-agent-framework</artifactId>
<version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. 基础配置

```java
@Configuration
public class AgentConfig {

    @Bean
    public OpenAIUnifiedChatClient openAIClient() {
        return new OpenAIUnifiedChatClient(
            "https://api.openai.com/v1", 
            "your-api-key"
        );
    }
    
    @Bean
    public CoreAgent coreAgent(OpenAIUnifiedChatClient client) {
        return CoreAgent.builder()
            .id("main-agent")
            .name("智能助手")
            .openAIUnifiedChatClient(client)
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
    private AgentConfig agentConfig;
    
    public Flux<AgentEvent> chat(String userId, String message) {
        UserChatRequest request = UserChatRequest.builder()
            .userId(userId)
            .conversationId("conv-" + userId)
            .message(new UserChatMessage(message))
            .build();
        
        AgentRunner runner = new AgentRunner(
            coreAgent, agentConfig, conversationService
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
.conversationTopic("技术讨论")
.tags(List.of("编程", "Java"))
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
.deploymentEnvironment("production")
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

### 职责分离设计

#### RunnerContext vs ConversationService

**RunnerContext 职责**：
- 执行期间的临时消息缓存
- 快速访问当前会话状态
- 本地消息历史管理
- 执行上下文维护

```java
// RunnerContext 主要用于执行期间
context.addMessage(new Message("user", "用户消息"));
context.addAssistantMessage("助手回复");
context.addToolMessage(toolCallId, "工具结果");

// 获取本地缓存的消息
List<Message> localMessages = context.getLocalMessageHistory();
```

**ConversationService 职责**：
- 持久化存储聊天记录
- 跨会话的历史查询
- 智能上下文窗口管理
- 会话元数据管理

```java
// ConversationService 负责持久化
conversationService.addMessage(conversationId, message);
conversationService.getConversationHistory(conversationId);
conversationService.getContextWindowMessages(conversationId, maxTokens, systemPrompt);
```

#### 协作流程

1. **消息添加流程**：
   ```
   用户消息 → ConversationService.addMessage() → RunnerContext.addMessage()
   ↓                              ↓
   持久化存储                      本地缓存
   ```

2. **消息获取流程**：
   ```
   获取完整历史 → ConversationService.getConversationHistory()
   获取上下文窗口 → ConversationService.getContextWindowMessages()
   获取本地缓存 → RunnerContext.getLocalMessageHistory()
   ```

3. **容错机制**：
   ```
   ConversationService 失败 → 降级到 RunnerContext 本地缓存
   保证基本功能正常 → 异步重试持久化
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
- 🔧 **职责分离优化**: RunnerContext 和 ConversationService 职责更清晰
- 🚀 **性能提升**: 优化消息加载和模板渲染性能
- 🛡️ **容错增强**: 更好的降级机制和错误处理

### 迁移指南

从 v0.0.1 升级到 v0.0.2：

1. **更新 AgentConfig**：
   ```java
   // 旧版本
   AgentConfig config = new AgentConfig();
   config.setStreamToolCallContent(true);

// 新版本 - 支持系统提示词模板
AgentConfig config = AgentConfig.builder()
.streamToolCallContent(true)
.systemPromptTemplate("你的自定义模板")
.build();
```

2. **更新 AgentRunner 使用方式**：
   ```java
   // 新版本会自动加载上下文信息，无需手动处理
   AgentRunner runner = new AgentRunner(coreAgent, config, conversationService);
   ```

3. **利用新的上下文信息**：
   ```java
   // 在自定义工具中可以访问丰富的上下文信息
   @Override
   public Mono<ToolResult> execute(ToolCall toolCall, ToolContext context) {
   RunnerContext runnerContext = context.getRunnerContext();
   String userId = runnerContext.getUserId();
   // 可以获取用户偏好、会话历史等信息
   return Mono.just(ToolResult.success(toolCall.getId(), "个性化结果"));
   }
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