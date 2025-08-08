package cn.apmen.jsagent.example.configuration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class MCPClientConfiguration {

    @Value("${agent.mcp.bingsearch.url}")
    private String bingSearchUrl;
    @Value("${agent.mcp.bingsearch.sse-endpoint}")
    private String bingSearchSseEndpoint;

    @Value("${agent.mcp.datetime.url}")
    private String datetimeUrl;
    @Value("${agent.mcp.datetime.sse-endpoint}")
    private String datetimeSseEndpoint;

    @Value("${agent.mcp.weather.url}")
    private String weatherUrl;
    @Value("${agent.mcp.weather.sse-endpoint}")
    private String weatherSseEndpoint;

    @Value("${agent.mcp.amap.url}")
    private String amapUrl;
    @Value("${agent.mcp.amap.sse-endpoint}")
    private String amapSseEndpoint;

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
     * 创建日期时间MCP客户端
     */
    @Bean(name = "datetimeMCPClient")
    public McpSyncClient datetimeMCPClient() {
        try {
            log.info("正在初始化日期时间MCP客户端...");

            McpSyncClient client = McpClient.sync(
                            HttpClientSseClientTransport.builder(datetimeUrl)
                                    .sseEndpoint(datetimeSseEndpoint)
                                    .build())
                    .requestTimeout(Duration.ofSeconds(60))
                    .initializationTimeout(Duration.ofSeconds(30))
                    .build();

            client.initialize();
            log.info("日期时间MCP客户端初始化成功");

            // 关闭时销毁资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    client.close();
                    log.info("日期时间MCP客户端已关闭");
                } catch (Exception e) {
                    log.error("关闭日期时间MCP客户端时发生错误", e);
                }
            }));

            return client;
        } catch (Exception e) {
            log.error("日期时间MCP客户端初始化失败", e);
            throw new RuntimeException("Failed to initialize DateTime MCP client: " + e.getMessage(), e);
        }
    }

    /**
     * 创建天气MCP客户端
     */
    @Bean(name = "weatherMCPClient")
    public McpSyncClient weatherMCPClient() {
        try {
            log.info("正在初始化天气MCP客户端...");

            McpSyncClient client = McpClient.sync(
                            HttpClientSseClientTransport.builder(weatherUrl)
                                    .sseEndpoint(weatherSseEndpoint)
                                    .build())
                    .requestTimeout(Duration.ofSeconds(60))
                    .initializationTimeout(Duration.ofSeconds(30))
                    .build();

            client.initialize();
            log.info("天气MCP客户端初始化成功");

            // 关闭时销毁资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    client.close();
                    log.info("天气MCP客户端已关闭");
                } catch (Exception e) {
                    log.error("关闭天气MCP客户端时发生错误", e);
                }
            }));

            return client;
        } catch (Exception e) {
            log.error("天气MCP客户端初始化失败", e);
            throw new RuntimeException("Failed to initialize Weather MCP client: " + e.getMessage(), e);
        }
    }

    /**
     * 创建高德地图MCP客户端
     */
    @Bean(name = "amapMCPClient")
    public McpSyncClient amapMCPClient() {
        try {
            log.info("正在初始化高德地图MCP客户端...");

            McpSyncClient client = McpClient.sync(
                            HttpClientSseClientTransport.builder(amapUrl)
                                    .sseEndpoint(amapSseEndpoint)
                                    .build())
                    .requestTimeout(Duration.ofSeconds(60))
                    .initializationTimeout(Duration.ofSeconds(30))
                    .build();

            client.initialize();
            log.info("高德地图MCP客户端初始化成功");

            // 关闭时销毁资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    client.close();
                    log.info("高德地图MCP客户端已关闭");
                } catch (Exception e) {
                    log.error("关闭高德地图MCP客户端时发生错误", e);
                }
            }));

            return client;
        } catch (Exception e) {
            log.error("高德地图MCP客户端初始化失败", e);
            throw new RuntimeException("Failed to initialize Amap MCP client: " + e.getMessage(), e);
        }
    }

}
