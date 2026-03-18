package com.yourcompany.mcp.tools;

import com.yourcompany.mcp.service.EsLogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具层：对外暴露给大模型的「工具」。
 *
 * 这里以一个简单的 `queryByRequestId` 工具为例，
 * 用于根据 requestId 从 Elasticsearch 中查询相关日志。
 *
 * 实际上需要根据 spring-ai-mcp-server 1.0.3 提供的注解 / 接口
 *（例如 @McpTool、ToolRequest/ToolResponse 模型等）进行适配。
 * 目前先以普通 Spring 组件的形式占位，后续你可以把方法签名
 * 改成对应 MCP 框架要求的格式。
 */
@Component
public class LogQueryTools {

    private final EsLogService esLogService;

    public LogQueryTools(EsLogService esLogService) {
        this.esLogService = esLogService;
    }

    /**
     * MCP 工具：根据 requestId + appName + iamTreePath 查询完整链路日志。
     *
     * @param requestId 请求 ID（必填）
     * @param appName 应用名称（必填）
     * @param iamTreePath IAM 树路径（必填）
     * @param level 日志级别过滤（可选）
     * @param size 返回条数限制（可选，默认 100）
     */
    @Tool(description = "根据 requestId + appName + iamTreePath 查询完整链路日志，自动遍历 Hera 里配置的所有 ES 索引，并在时间窗口内聚合返回。")
    public Map<String, Object> queryFullTraceByRequestId(
            String requestId,
            String appName,
            String iamTreePath,
            @Nullable String level,
            @Nullable Integer size
    ) throws IOException {

        Map<String, List<Map<String, Object>>> data = esLogService.queryFullTraceByRequestId(
                requestId,
                appName,
                iamTreePath,
                level,
                size
        );

        Map<String, Object> result = new HashMap<>();
        result.put("requestId", requestId);
        result.put("appName", appName);
        result.put("iamTreePath", iamTreePath);
        result.put("level", level);
        result.put("size", size);
        result.put("indices", data.keySet());
        result.put("logsByIndex", data);
        return result;
    }
}

