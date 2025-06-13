# JS Agent 框架使用案例

这是一个基于 `js-agent-framework` 的完整使用案例，展示了如何构建和使用企业级Agent框架。

## 项目结构

\`\`\`
js-agent/
├── src/main/java/cn/apmen/jsagent/
│   ├── JsAgentApplication.java          # 主应用类
│   ├── config/
│   │   ├── AgentConfig.java             # Agent框架配置
│   │   └── ToolConfig.java              # 工具注册配置
│   ├── controller/
│   │   └── ChatController.java          # REST API控制器
│   ├── service/
│   │   └── AgentTestService.java        # 测试服务
│   └── tools/
│       ├── CalculatorTool.java          # 计算器工具
│       └── WeatherTool.java             # 天气查询工具
├── src/main/resources/
│   ├── application.yml                  # 应用配置
│   └── static/
│       └── index.html                   # 测试页面
└── README.md
\`\`\`

## 功能特性

### 1. 核心功能
- **Agent对话**: 基于LLM的智能对话
- **工具调用**: 支持自定义工具扩展
- **流式响应**: 支持实时流式输出
- **对话管理**: 维护对话上下文和历史

### 2. 内置工具
- **计算器工具**: 支持基本数学运算（加减乘除）
- **天气查询工具**: 模拟天气查询功能

### 3. API接口
- `POST /api/chat/message` - 非流式聊天接口
- `POST /api/chat/stream` - 流式聊天接口

## 快速开始

### 1. 环境要求
- Java 17+
- Maven 3.6+
- Spring Boot 3.x

### 2. 配置设置

在 `application.yml` 中配置OpenAI API密钥：

\`\`\`yaml
agent:
  llm:
    openai:
      api-key: "your-openai-api-key"
\`\`\`

或者设置环境变量：
\`\`\`bash
export OPENAI_API_KEY="your-openai-api-key"
\`\`\`

### 3. 启动应用

\`\`\`bash
# 编译项目
mvn clean compile

# 启动应用
mvn spring-boot:run
\`\`\`

应用启动后，访问 http://localhost:8080 查看测试页面。

## 使用示例

### 1. 基本对话
\`\`\`bash
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "conversationId": "test-conversation",
    "message": "你好，请介绍一下你自己"
  }'
\`\`\`

### 2. 计算器工具调用
\`\`\`bash
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "conversationId": "test-conversation",
    "message": "请帮我计算 25 + 17 的结果"
  }'
\`\`\`

### 3. 天气查询工具调用
\`\`\`bash
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "conversationId": "test-conversation",
    "message": "请查询北京的天气情况"
  }'
\`\`\`

### 4. 流式响应
\`\`\`bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "conversationId": "test-conversation",
    "message": "请详细介绍一下你的功能"
  }'
\`\`\`

## 自定义工具开发

### 1. 创建工具类

实现 `ToolExecutor` 接口：

\`\`\`java
@Component
@Slf4j
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
        // 实现工具逻辑
        return Mono.just(ToolResult.success(toolCall.getId(), "执行结果"));
    }
}
\`\`\`

### 2. 注册工具

在 `ToolConfig` 中注册新工具：

\`\`\`java
@Autowired
private CustomTool customTool;

@Override
public void run(String... args) throws Exception {
    toolRegistry.registerExecutor(customTool);
}
\`\`\`

### 3. 添加工具定义

在 `AgentConfig` 的 `toolDefinitions()` 方法中添加工具定义。

## 测试验证

### 1. 自动测试
应用启动时会自动运行 `AgentTestService` 进行功能测试。

### 2. Web界面测试
访问 http://localhost:8080 使用Web界面进行交互测试。

### 3. API测试
使用上述curl命令或Postman进行API测试。

## 架构说明

### 1. 核心组件
- **CoreAgent**: 核心Agent，负责LLM调用
- **AgentRunner**: Agent运行器，控制整个对话流程
- **ToolRegistry**: 工具注册器，管理所有工具
- **ConversationService**: 对话服务，管理对话历史

### 2. 工具系统
- **ToolExecutor**: 工具执行器接口
- **ToolContext**: 工具执行上下文
- **ToolResult**: 工具执行结果

### 3. 流式处理
- **SSEParser**: 服务端事件解析器
- **StreamAccumulator**: 流式响应累积器

## 扩展指南

### 1. 添加新的LLM提供商
实现 `OpenAIUnifiedChatClient` 的替代版本。

### 2. 自定义对话存储
实现 `ConversationService` 接口，支持数据库存储。

### 3. 添加认证授权
在控制器层添加Spring Security配置。

### 4. 监控和日志
集成Micrometer和ELK栈进行监控。

## 故障排除

### 1. 常见问题
- **API密钥错误**: 检查OpenAI API密钥配置
- **网络连接**: 确保能访问OpenAI API
- **工具未注册**: 检查工具是否正确注册到ToolRegistry

### 2. 日志调试
设置日志级别为DEBUG查看详细信息：
\`\`\`yaml
logging:
  level:
    cn.apmen.jsagent: DEBUG
\`\`\`

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 许可证

本项目采用MIT许可证。