package com.yourcompany.mcp.test;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.TransportException;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.yourcompany.mcp.service.EsLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * 一个简单的本地测试入口，用于直接调用 EsLogService，
 * 验证是否可以正常通过 Elasticsearch Java Client 执行查询。
 *
 * 使用方法：
 * 1. 确认 ES 地址、端口、协议（默认 http://192.168.171.129:9200）。
 * 2. 修改下面常量中的配置（索引名、requestId 等）。
 * 3. 通过 IDE 右键运行本类的 main 方法，查看控制台输出。
 */
public class EsLogServiceManualTest {

    // ======= 请按实际情况修改以下参数 =======
    private static final String ES_HOST = "192.168.171.129";
    private static final int ES_PORT = 9200;
    private static final String ES_SCHEME = "http"; // 如果是 https，请改为 "https"

    // 示例：用于测试的索引名称和 requestId
    private static final String INDEX_NAME = "payment-log-**";
    private static final String REQUEST_ID = "126e0d09863a469eb808d36de36a86fa";
    private static final int QUERY_SIZE = 20;
    // ====================================

    public static void main(String[] args) throws IOException {
        System.out.println("==== EsLogServiceManualTest 启动 ====");
        System.out.printf("ES 连接配置 -> host=%s, port=%d, scheme=%s%n", ES_HOST, ES_PORT, ES_SCHEME);
        System.out.printf("查询参数 -> index=%s, requestId=%s, size=%d%n", INDEX_NAME, REQUEST_ID, QUERY_SIZE);

        // 1. 构建底层 RestClient
        System.out.println("[1] 构建 RestClient...");
        RestClient restClient = RestClient.builder(
                new HttpHost(ES_HOST, ES_PORT, ES_SCHEME)
        ).build();

        // 2. 使用 JacksonJsonpMapper 构建 Transport
        System.out.println("[2] 构建 RestClientTransport (JacksonJsonpMapper)...");
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );

        // 3. 构建 ElasticsearchClient
        System.out.println("[3] 构建 ElasticsearchClient 实例...");
        ElasticsearchClient client = new ElasticsearchClient(transport);

        // 4. 创建 EsLogService 并发起查询
        System.out.println("[4] 创建 EsLogService，并准备发起 queryByRequestId 调用...");
        EsLogService esLogService = new EsLogService(client);

        try {
            System.out.println("[5] 开始执行 ES 查询...");
            List<Map<String, Object>> logs =
                    esLogService.queryByRequestId(INDEX_NAME, REQUEST_ID, QUERY_SIZE);

            System.out.printf("[6] 查询完成，共返回 %d 条结果%n", logs.size());

            for (int i = 0; i < logs.size(); i++) {
                System.out.printf("---- 结果 #%d ----%n", i + 1);
                Map<String, Object> log = logs.get(i);
                log.forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
                System.out.println();
            }

            if (logs.isEmpty()) {
                System.out.println("[提示] 没有查询到任何结果，请检查：");
                System.out.println("  - INDEX_NAME 是否正确（是否包含通配符 *）");
                System.out.println("  - REQUEST_ID 是否真实存在于索引中");
                System.out.println("  - ES 中该索引是否有数据");
            }
        } catch (TransportException e) {
            // 典型场景：ES 7.12.x 不会返回 X-Elastic-Product，elasticsearch-java 会直接拒绝。
            // 为了让手动测试在旧版本 ES 上也能跑通，这里自动降级为 RestClient 直连 _search。
            if (e.getMessage() != null && e.getMessage().contains("Missing [X-Elastic-Product] header")) {
                System.err.println("[警告] 当前 ES 版本可能较旧（如 7.12.x），elasticsearch-java 会因为缺少 X-Elastic-Product 头而拒绝响应。");
                System.err.println("[警告] 将自动降级为 RestClient 直连 _search 以完成本次手工验证。");

                List<Map<String, Object>> logs = queryByRequestIdViaRestClient(restClient, INDEX_NAME, REQUEST_ID, QUERY_SIZE);
                System.out.printf("[6]（降级）查询完成，共返回 %d 条结果%n", logs.size());
                for (int i = 0; i < logs.size(); i++) {
                    System.out.printf("---- 结果 #%d ----%n", i + 1);
                    Map<String, Object> log = logs.get(i);
                    log.forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
                    System.out.println();
                }
            } else {
                System.err.println("[错误] ES 查询发生 TransportException：");
                e.printStackTrace();
            }
        } catch (ElasticsearchException e) {
            System.err.println("[错误] ES 查询发生 ElasticsearchException：");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("[错误] ES 查询发生 IO 异常（可能是网络/连接问题）：");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[错误] 发生未预期的异常：");
            e.printStackTrace();
        } finally {
            // 5. 关闭底层客户端
            System.out.println("[7] 关闭 Elasticsearch 客户端连接...");
            try {
                transport.close();
            } catch (Exception e) {
                System.err.println("[警告] 关闭 transport 时发生异常：");
                e.printStackTrace();
            }
            try {
                restClient.close();
            } catch (Exception e) {
                System.err.println("[警告] 关闭 restClient 时发生异常：");
                e.printStackTrace();
            }
            System.out.println("==== EsLogServiceManualTest 结束 ====");
        }
    }

    private static List<Map<String, Object>> queryByRequestIdViaRestClient(
            RestClient restClient,
            String indexName,
            String requestId,
            int size
    ) throws IOException {
        int finalSize = size <= 0 ? 50 : size;

        String body = String.format(
                "{\n" +
                        "  \"size\": %d,\n" +
                        "  \"query\": {\n" +
                        "    \"term\": { \"requestId\": \"%s\" }\n" +
                        "  },\n" +
                        "  \"sort\": [\n" +
                        "    { \"@timestamp\": { \"order\": \"desc\" } }\n" +
                        "  ]\n" +
                        "}\n",
                finalSize,
                escapeJson(requestId)
        );

        Request request = new Request("POST", "/" + indexName + "/_search");
        request.setJsonEntity(body);

        Response response = restClient.performRequest(request);
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(response.getEntity().getContent());
        JsonNode hits = root.path("hits").path("hits");

        List<Map<String, Object>> results = new ArrayList<>();
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode src = hit.get("_source");
                if (src != null && src.isObject()) {
                    results.add((Map<String, Object>) (Map) om.convertValue(src, Map.class));
                }
            }
        }
        return results;
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        // 足够用于 requestId 这类简单字符串场景
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

