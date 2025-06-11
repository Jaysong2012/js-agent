# JS Agent 项目

这是一个多模块的Maven项目，包含以下模块：

## 模块结构

### 1. js-agent-parent
- 父模块，管理整个项目的依赖和构建配置
- 包含两个子模块的定义

### 2. js-agent-framework
- 框架模块，提供JS Agent的核心功能
- 包含基础的框架组件和自动配置
- 其他模块可以依赖此框架模块

### 3. js-agent
- 应用模块，基于js-agent-framework构建的具体应用
- 包含Spring Boot主启动类
- 依赖js-agent-framework模块

## 构建项目

\`\`\`bash
# 编译整个项目
./mvnw clean compile

# 打包整个项目
./mvnw clean package

# 运行应用
./mvnw spring-boot:run -pl js-agent
\`\`\`

## 项目结构

\`\`\`
js-agent/
├── pom.xml                           # 父模块POM
├── js-agent-framework/               # 框架模块
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── cn/apmen/jsagent/framework/
│       │   └── resources/
│       │       └── META-INF/
│       └── test/
└── js-agent/                         # 应用模块
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── cn/apmen/jsagent/
        │   └── resources/
        └── test/
\`\`\`