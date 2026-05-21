package org.springframework.ai.mcp.client.autoconfigure;

import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 用户类加载器优先加载当前classpath：classes下类，然后加载jar包类
 * 自定义MCP SSE传输配置，支持自定义请求头
 */
@AutoConfiguration
@ConditionalOnClass({WebFluxSseClientTransport.class})
@EnableConfigurationProperties({McpSseClientProperties.class, McpClientCommonProperties.class})
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.client",
        name = {"enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class SseWebFluxTransportAutoConfiguration {
    @Bean
    public List<NamedClientMcpTransport> webFluxClientTransports(McpSseClientProperties sseProperties, ObjectProvider<WebClient.Builder> webClientBuilderProvider) {
        List<NamedClientMcpTransport> sseTransports = new ArrayList<>();
        WebClient.Builder webClientBuilderTemplate = webClientBuilderProvider.getIfAvailable(WebClient::builder);

        for (Map.Entry<String, McpSseClientProperties.SseParameters> serverParameters : sseProperties.getConnections().entrySet()) {
            WebClient.Builder webClientBuilder = webClientBuilderTemplate.clone()
                    .baseUrl(serverParameters.getValue().url());
            String sseEndpoint = serverParameters.getValue().sseEndpoint() != null ? serverParameters.getValue().sseEndpoint() : "/sse";
            WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder(webClientBuilder).sseEndpoint(sseEndpoint).build();

            sseTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
        }

        return sseTransports;
    }
}
