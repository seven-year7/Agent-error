package com.yourcompany.mcp.config;

import org.springframework.context.annotation.Configuration;

/**
 * MCP 协议 / Spring AI MCP Server 相关配置。
 *
 * 实际上具体的配置项（例如工具自动扫描包、连接模式等）
 * 需要根据 spring-ai-mcp-server 1.0.3 版本的官方文档调整。
 * 这里先保留一个配置类，后续可以在此添加 @Bean 或属性绑定。
 */
@Configuration
public class McpServerConfig {

    // 例如：
    // @Bean
    // public McpServerProperties mcpServerProperties() { ... }
    //
    // 或者通过 @ConfigurationProperties(prefix = "spring.ai.mcp.server")
    // 直接映射到属性类。
}

