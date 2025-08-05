package cn.apmen.jsagent.example.configuration;

import cn.apmen.jsagent.framework.mcp.MCPTool;
import cn.apmen.jsagent.framework.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP工具配置 - 使用新的工具设计模式
 * 展示如何配置和使用MCPTool
 *
 * 注意：这些Bean只有在配置了相应的enabled属性时才会创建
 * 用户需要根据实际情况提供McpClientTransport实现
 */
@Slf4j
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
        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> pathParam = new HashMap<>();
        pathParam.put("type", "string");
        pathParam.put("description", "要读取的文件路径");
        properties.put("path", pathParam);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"path"});

        // 创建MCPTool，设置directOutput=true，直接输出给用户
        return new MCPTool(
            "read_file",
            "读取指定路径的文件内容，直接返回给用户",
            parameters,
            new String[]{"path"},
            fileSystemMCPClient,
            "read_file"
        );
    }

    /**
     * 创建文件写入MCPTool - 返回给主Agent
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.filesystem.enabled", havingValue = "true", matchIfMissing = false)
    public MCPTool fileWriteMCPTool(McpSyncClient fileSystemMCPClient) {
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
        parameters.put("required", new String[]{"path", "content"});

        // 创建MCPTool，设置directOutput=false，返回给主Agent
        return new MCPTool(
            "write_file",
            "将内容写入到指定路径的文件",
            parameters,
            new String[]{"path", "content"},
            fileSystemMCPClient,
            "write_file"
        );
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
        // 定义参数结构
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> sqlParam = new HashMap<>();
        sqlParam.put("type", "string");
        sqlParam.put("description", "要执行的SQL查询语句");
        properties.put("sql", sqlParam);

        parameters.put("properties", properties);
        parameters.put("required", new String[]{"sql"});

        // 创建MCPTool，设置directOutput=true，直接输出给用户
        return new MCPTool(
            "query_database",
            "执行SQL查询并返回结果",
            parameters,
            new String[]{"sql"},
            databaseMCPClient,
            "query_database"
        );
    }

    /**
     * 创建Bing搜索MCP客户端
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.bingsearch.enabled", havingValue = "true", matchIfMissing = false)
    public McpSyncClient bingSearchMCPClient() {
        McpClientTransport transport = createBingSearchTransport();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(60))
                .initializationTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 创建Bing搜索MCPTool - 直接输出给用户
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.bingsearch.enabled", havingValue = "true", matchIfMissing = false)
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
            "bing_search"
        );
    }

    /**
     * 初始化MCP工具注册
     */
    //@Bean
    public String initializeMCPTools(ToolRegistry toolRegistry,
                                    MCPTool fileReadMCPTool,
                                    MCPTool fileWriteMCPTool,
                                    MCPTool dbQueryMCPTool,
                                    MCPTool bingSearchMCPTool) {

        log.info("开始注册MCP工具到ToolRegistry...");

        int registeredCount = 0;

        // 注册文件系统工具（如果启用）
        if (fileReadMCPTool != null) {
            toolRegistry.registerExecutor(fileReadMCPTool);
            registeredCount++;
        }

        if (fileWriteMCPTool != null) {
            toolRegistry.registerExecutor(fileWriteMCPTool);
            registeredCount++;
        }

        // 注册数据库工具（如果启用）
        if (dbQueryMCPTool != null) {
            toolRegistry.registerExecutor(dbQueryMCPTool);
            registeredCount++;
        }

        // 注册搜索工具（如果启用）
        if (bingSearchMCPTool != null) {
            toolRegistry.registerExecutor(bingSearchMCPTool);
            registeredCount++;
        }

        log.info("MCP工具注册完成，共注册 {} 个MCP工具", registeredCount);

        return "mcp-tools-initialized";
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
        // return new SseClientTransport("http://localhost:8080/mcp/filesystem");
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
        // 例如：
        // return new StdioClientTransport(
        //     Arrays.asList("python", "/path/to/database-server.py"),
        //     Map.of("DB_CONNECTION_STRING", "postgresql://...")
        // );
        throw new UnsupportedOperationException(
            "请实现createDatabaseTransport方法，提供具体的McpClientTransport实现。" +
            "或者在application.yml中设置 mcp.database.enabled=false 来禁用MCP数据库功能。");
    }

    /**
     * 创建Bing搜索传输层 - 示例实现
     * 用户需要根据实际情况实现
     */
    private McpClientTransport createBingSearchTransport() {
        // 这里是示例代码，用户需要根据实际情况实现
        // 例如：
        // return new SseClientTransport("http://localhost:8080/mcp/bingsearch");
        throw new UnsupportedOperationException(
            "请实现createBingSearchTransport方法，提供具体的McpClientTransport实现。" +
            "或者在application.yml中设置 mcp.bingsearch.enabled=false 来禁用MCP Bing搜索功能。");
    }
}