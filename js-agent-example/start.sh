#!/bin/bash

echo "启动 JS Agent 应用..."

# 设置环境变量
export OPENAI_API_KEY="your-openai-api-key-here"

# 编译项目
echo "编译项目..."
mvn clean compile -DskipTests

if [ $? -ne 0 ]; then
    echo "编译失败，请检查错误信息"
    exit 1
fi

echo "编译成功，启动应用..."

# 启动应用
mvn spring-boot:run

echo "应用已启动，访问 http://localhost:8080 查看测试页面"