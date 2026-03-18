package com.yourcompany.mcp;

import com.yourcompany.mcp.service.HelloService;
import com.yourcompany.mcp.tools.LogQueryTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class EsMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(EsMcpApplication.class, args);
    }

    /**
     * 注册所有基于 @Tool 注解的方法为 MCP 工具（包括 HelloService 与 LogQueryTools）。
     */
    @Bean
    public ToolCallbackProvider mcpTools(HelloService helloService, LogQueryTools logQueryTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(helloService, logQueryTools)
                .build();
    }
}

