package com.yourcompany.mcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
public class HelloService {

    @Tool(description = "返回一个简单的 Hello World 文本，name 为空则使用默认称呼。")
    public String hello(String name) {
        // 兼容低版本 JDK：用 trim().isEmpty() 替代 String.isBlank()
        if (name == null || name.trim().isEmpty()) {
            return "Hello, MCP World，变则通!";
        }
        return "Hello, " + name + "!";
    }
}

