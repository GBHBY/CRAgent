package cn.gyb.llm.cr.agent.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;

/**
 * Notion MCP 客户端配置，初始化 MCP 连接并注册工具回调。
 * <p>
 * 通过 HTTP Streamable Transport 连接 Notion MCP 服务器，
 * 初始化完成后将 Notion 工具回调注册为 Spring Bean，供 Agent 使用。
 * 初始化失败时不会阻止应用启动，仅记录警告日志。
 */
@Slf4j
@Configuration
public class McpClientConfig implements InitializingBean {

    /** Notion MCP 服务器地址 */
    @Value("${notion.mcp.url}")
    private String notionMcpUrl;

    /** Notion API 密钥，用于请求鉴权 */
    @Value("${notion.mcp.api-key}")
    private String notionApiKey;

    /** Notion MCP 工具回调数组，初始化后由 afterPropertiesSet 填充 */
    private ToolCallback[] notionToolCallbacks;

    /**
     * Bean 初始化完成后执行，建立 MCP 连接并加载工具回调。
     * 连接失败时将 notionToolCallbacks 置为 null，Notion 相关功能将不可用。
     */
    @Override
    public void afterPropertiesSet() {
        try {
            log.info("初始化 Notion MCP 客户端，url={}", notionMcpUrl);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + notionApiKey)
                    .header("Notion-Version", "2022-06-28");

            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(notionMcpUrl)
                    .requestBuilder(requestBuilder)
                    .build();

            McpSyncClient mcpClient = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(300))
                    .build();

            mcpClient.initialize();

            SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                    .mcpClients(List.of(mcpClient))
                    .build();

            notionToolCallbacks = provider.getToolCallbacks();

            log.info("Notion MCP 客户端初始化成功，加载了 {} 个工具回调",
                    notionToolCallbacks != null ? notionToolCallbacks.length : 0);
        } catch (Exception e) {
            log.warn("初始化 Notion MCP 客户端失败，Notion 文档创建将不可用: {}",
                    e.getMessage(), e);
            notionToolCallbacks = null;
        }
    }

    /**
     * 获取 Notion MCP 工具回调数组，供其他组件注入使用。
     *
     * @return Notion 工具回调数组，初始化失败时返回 null
     */
    @Bean
    public ToolCallback[] getNotionToolCallbacks() {
        return notionToolCallbacks;
    }
}
