package com.yourcompany.mcp.test;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import com.yourcompany.mcp.service.EsLogService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 一个简单的本地测试入口，用于直接调用 EsLogService，
 * 验证本机是否可以正常通过 Elasticsearch Java Client 执行查询。
 *
 * 使用方法：
 * 1. 确认本机 ES 地址、端口、协议（默认 http://localhost:9200）。
 * 2. 修改下面 MAIN_* 常量中的配置（索引名、requestId 等）。
 * 3. 通过 IDE 右键运行本类的 main 方法，查看控制台输出。
 */
public class EsLogServiceManualTest {

    // ======= 请按实际情况修改以下参数 =======
    private static final String ES_HOST = "localhost";
    private static final int ES_PORT = 9200;
    private static final String ES_SCHEME = "http"; // 如果是 https，请改为 "https"

    // 示例：用于测试的索引名称和 requestId
    private static final String INDEX_NAME = "your-log-index-*";
    private static final String REQUEST_ID = "your-test-request-id";
    private static final int QUERY_SIZE = 20;
    // ====================================

    public static void main(String[] args) throws IOException {
        // 1. 构建底层 RestClient
        RestClient restClient = RestClient.builder(
                new HttpHost(ES_HOST, ES_PORT, ES_SCHEME)
        ).build();

        // 2. 使用 JacksonJsonpMapper 构建 Transport
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        // 3. 构建 ElasticsearchClient
        ElasticsearchClient client = new ElasticsearchClient(transport);

        // 4. 创建 EsLogService 并发起查询
        EsLogService esLogService = new EsLogService(client);

        try {
            System.out.printf("开始查询 ES，index=%s, requestId=%s, size=%d%n",
                    INDEX_NAME, REQUEST_ID, QUERY_SIZE);

            List<Map<String, Object>> logs =
                    esLogService.queryByRequestId(INDEX_NAME, REQUEST_ID, QUERY_SIZE);

            System.out.printf("查询完成，共返回 %d 条结果%n", logs.size());

            for (int i = 0; i < logs.size(); i++) {
                System.out.printf("---- 结果 #%d ----%n", i + 1);
                Map<String, Object> log = logs.get(i);
                log.forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
                System.out.println();
            }

            if (logs.isEmpty()) {
                System.out.println("没有查询到任何结果，请检查索引名 / requestId / 时间范围等是否正确。");
            }
        } catch (ElasticsearchException e) {
            System.err.println("ES 查询发生 ElasticsearchException：");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("ES 查询发生 IO 异常：");
            e.printStackTrace();
        } finally {
            // 5. 关闭底层客户端
            transport.close();
            restClient.close();
        }
    }
}

