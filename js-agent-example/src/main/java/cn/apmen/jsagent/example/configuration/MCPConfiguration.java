package cn.apmen.jsagent.example.configuration;

import cn.apmen.jsagent.framework.mcp.MCPTool;
import cn.apmen.jsagent.framework.openaiunified.model.request.Function;
import cn.apmen.jsagent.framework.openaiunified.model.request.Tool;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP工具配置示例
 * 展示如何配置和使用MCPTool
 *
 * 注意：这些Bean只有在配置了mcp.enabled=true时才会创建
 * 用户需要根据实际情况提供McpClientTransport实现
 */
@Configuration
public class MCPConfiguration {

    /**
     * 创建文件系统MCP客户端
     * 只有在配置了mcp.filesystem.enabled=true时才创建
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.filesystem.enabled", havingValue = "true", matchIfMissing = false)
    public McpSyncClient fileSystemMCPClient() {
        // 用户需要根据实际情况提供transport
        // 例如：StdioClientTransport transport = new StdioClientTransport(command, args);
        // 或者：SseClientTransport transport = new SseClientTransport(url);
        McpClientTransport transport = createFileSystemTransport();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 创建文件读取MCPTool - 直接输出给用户
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.filesystem.enabled", havingValue = "true", matchIfMissing = false)
    public MCPTool fileReadMCPTool(McpSyncClient fileSystemMCPClient) {
        // 定义Tool结构
        Tool toolDefinition = new Tool();
        toolDefinition.setType("function");

        Function function = new Function();
        function.setName("read_file");
        function.setDescription("读取指定路径的文件内容，直接返回给用户");

        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> pathParam = new HashMap<>();
        pathParam.put("type", "string");
        pathParam.put("description", "要读取的文件路径");
        properties.put("path", pathParam);

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path"));

        function.setParameters(parameters);
        toolDefinition.setFunction(function);

        // 创建MCPTool，directOutput=true表示直接输出给用户
        return new MCPTool(toolDefinition, fileSystemMCPClient, "read_file", true);
    }

    /**
     * 创建文件写入MCPTool - 返回给主Agent
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.filesystem.enabled", havingValue = "true", matchIfMissing = false)
    public MCPTool fileWriteMCPTool(McpSyncClient fileSystemMCPClient) {
        // 定义Tool结构
        Tool toolDefinition = new Tool();
        toolDefinition.setType("function");

        Function function = new Function();
        function.setName("write_file");
        function.setDescription("将内容写入到指定路径的文件");

        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> pathParam = new HashMap<>();
        pathParam.put("type", "string");
        pathParam.put("description", "要写入的文件路径");
        properties.put("path", pathParam);
        Map<String, Object> contentParam = new HashMap<>();
        contentParam.put("type", "string");
        contentParam.put("description", "要写入的文件内容");
        properties.put("content", contentParam);

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("path", "content"));

        function.setParameters(parameters);
        toolDefinition.setFunction(function);

        // 创建MCPTool，directOutput=false表示返回给主Agent
        return new MCPTool(toolDefinition, fileSystemMCPClient, "write_file", false);
    }

    /**
     * 创建数据库查询MCP客户端
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.database.enabled", havingValue = "true", matchIfMissing = false)
    public McpSyncClient databaseMCPClient() {
        McpClientTransport transport = createDatabaseTransport();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 创建数据库查询MCPTool - 直接输出给用户
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.database.enabled", havingValue = "true", matchIfMissing = false)
    public MCPTool dbQueryMCPTool(McpSyncClient databaseMCPClient) {
        // 定义Tool结构
        Tool toolDefinition = new Tool();
        toolDefinition.setType("function");

        Function function = new Function();
        function.setName("query_database");
        function.setDescription("执行SQL查询并返回结果");

        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> sqlParam = new HashMap<>();
        sqlParam.put("type", "string");
        sqlParam.put("description", "要执行的SQL查询语句");
        properties.put("sql", sqlParam);

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("sql"));

        function.setParameters(parameters);
        toolDefinition.setFunction(function);

        // 创建MCPTool，directOutput=true表示直接输出给用户
        return new MCPTool(toolDefinition, databaseMCPClient, "query_database", true);
    }
    /**
     * 创建文件系统传输层 - 示例实现
     * 用户需要根据实际情况实现
     */
    private McpClientTransport createFileSystemTransport() {
        // 这里是示例代码，用户需要根据实际情况实现
        // 例如：
        // return new StdioClientTransport(
        //     Arrays.asList("node", "/path/to/filesystem-server.js"),
        //     Map.of("WORKSPACE_PATH", "/workspace")
        // );
        // 或者使用SSE传输：
        // return new SseClientTransport("http://localhost:8080/mcp");
        throw new UnsupportedOperationException(
            "请实现createFileSystemTransport方法，提供具体的McpClientTransport实现。" +
            "或者在application.yml中设置 mcp.filesystem.enabled=false 来禁用MCP文件系统功能。");
    }
    /**
     * 创建数据库传输层 - 示例实现
     * 用户需要根据实际情况实现
     */
    private McpClientTransport createDatabaseTransport() {
        // 这里是示例代码，用户需要根据实际情况实现
        throw new UnsupportedOperationException(
            "请实现createDatabaseTransport方法，提供具体的McpClientTransport实现。" +
            "或者在application.yml中设置 mcp.database.enabled=false 来禁用MCP数据库功能。");
    }
}