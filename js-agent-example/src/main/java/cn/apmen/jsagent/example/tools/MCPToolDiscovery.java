package cn.apmen.jsagent.example.tools;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * MCP工具发现类 - 用于查询MCP服务器的工具列表
 */
@Slf4j
public class MCPToolDiscovery {

    public static void main(String[] args) {
        // 定义要查询的MCP服务器
        String[] mcpServers = {
            "http://mcphub-server.sankuai.com", // 日期时间服务
            "http://mcphub-server.sankuai.com", // 天气服务
            "https://mcp.amap.com/mcp?key=6438348d76bc474a4c47d0d6d7001d65" // 高德地图服务
        };

        String[] mcpSSEEndpoints = {
                "/mcphub-api/e1db9ddf31d24b", // 日期时间服务
                "/mcphub-api/eead0e8084b54b", // 天气服务
                "" // 高德地图服务
        };

        String[] serverNames = {
            "日期时间服务",
            "天气服务",
            "高德地图服务"
        };

        for (int i = 0; i < mcpServers.length; i++) {
            try {
                log.info("正在查询 {} 的工具列表...", serverNames[i]);
                discoverTools(mcpServers[i], mcpSSEEndpoints[i], serverNames[i]);
                log.info("完成查询 {}\n", serverNames[i]);
            } catch (Exception e) {
                log.error("查询 {} 失败: {}", serverNames[i], e.getMessage(), e);
            }
        }
    }

    private static void discoverTools(String serverUrl, String sseEndpoint,String serverName) {
        McpSyncClient client = null;
        try {
            // 创建MCP客户端
            client = McpClient.sync(
                    HttpClientSseClientTransport.builder(serverUrl)
                            //.sseEndpoint(sseEndpoint)
                            .build())
                    .requestTimeout(Duration.ofSeconds(30))
                    .initializationTimeout(Duration.ofSeconds(15))
                    .build();

            // 初始化客户端
            client.initialize();
            log.info("{} 客户端初始化成功", serverName);

            // 查询工具列表
            try {
                var result = client.listTools();
                log.info("{} 工具列表查询成功", serverName);
                log.info("工具列表结果: {}", result);
            } catch (Exception e) {
                log.error("调用listTools失败", e);
                // 尝试其他方法获取工具信息
                log.info("尝试获取服务器信息...");
                try {
                    var serverInfo = client.toString();
                    log.info("服务器信息: {}", serverInfo);
                } catch (Exception ex) {
                    log.error("获取服务器信息失败", ex);
                }
            }

        } catch (Exception e) {
            log.error("查询 {} 工具列表失败", serverName, e);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("关闭 {} 客户端失败", serverName, e);
                }
            }
        }
    }
}

