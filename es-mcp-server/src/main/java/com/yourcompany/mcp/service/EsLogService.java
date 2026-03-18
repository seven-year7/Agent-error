package com.yourcompany.mcp.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务层：封装针对日志索引的 ES 查询逻辑。
 *
 * 示例：根据 requestId 查询最近一条 / 多条日志。
 */
@Service
public class EsLogService {

    private final ElasticsearchClient esClient;

    public EsLogService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * 按 requestId 查询日志（示例：返回最近 50 条）。
     *
     * @param indexName 日志索引名
     * @param requestId 请求 ID
     * @param size      返回条数
     */
    public List<Map<String, Object>> queryByRequestId(String indexName, String requestId, int size) throws IOException {
        if (size <= 0) {
            size = 50;
        }

        Query termQuery = Query.of(q -> q
                .term(t -> t
                        .field("requestId")
                        .value(requestId)
                )
        );

        // 避免 lambda 捕获到可变局部变量（某些编译配置会报错），先收敛为 final。
        final int finalSize = size;

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .size(finalSize)
                .query(termQuery)
                .sort(sort -> sort
                        .field(f -> f
                                .field("@timestamp")
                                .order(SortOrder.Desc)
                        )
                )
        );

        // elasticsearch-java 的 search(Class<TDocument>) 需要传入“原始类型”的 Class（这里是 Map.class），
        // 所以响应类型用原始 Map 接收，再在返回时收敛为 Map<String, Object>。
        SearchResponse<Map> response = esClient.search(searchRequest, Map.class);

        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(src -> src != null)
                .map(src -> (Map<String, Object>) src)
                .collect(Collectors.toList());
    }

    /**
     * 根据 RequestId 查询完整链路日志：
     * 1. 先从 TraceLog 索引中查出该 RequestId 的时间戳；
     * 2. 再根据 appName + iamTreePath 从 Hera 获取所有相关索引；
     * 3. 对每个索引在时间窗口（timestamp ±5s）内按 requestId（可选 level）查询日志；
     * 4. 仅返回有数据的索引。
     */
    public Map<String, List<Map<String, Object>>> queryFullTraceByRequestId(
            String requestId,
            String appName,
            String iamTreePath,
            @Nullable String level,
            @Nullable Integer size
    ) throws IOException {

        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("appName is required");
        }
        if (iamTreePath == null || iamTreePath.isBlank()) {
            throw new IllegalArgumentException("iamTreePath is required");
        }

        int limit = (size == null || size <= 0) ? 100 : size;

        Instant traceTs = findTraceTimestampByRequestId(requestId);
        if (traceTs == null) {
            return Collections.emptyMap();
        }

        List<String> indices = findEsIndicesFromHera(appName, iamTreePath);
        if (indices == null || indices.isEmpty()) {
            return Collections.emptyMap();
        }

        Instant from = traceTs.minusSeconds(5);
        Instant to = traceTs.plusSeconds(5);

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String index : indices) {
            List<Map<String, Object>> logs = queryLogsInIndex(index, requestId, from, to, level, limit);
            if (logs != null && !logs.isEmpty()) {
                result.put(index, logs);
            }
        }

        return result;
    }

    /**
     * 在 TraceLog 索引中按 requestId 找到最近的一条记录时间戳。
     * 这里默认 TraceLog 索引名为 "tracelog-*"，时间字段为 "@timestamp"。
     */
    @Nullable
    protected Instant findTraceTimestampByRequestId(String requestId) throws IOException {
        Query termQuery = Query.of(q -> q
                .term(t -> t
                        .field("requestId")
                        .value(requestId)
                )
        );

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index("tracelog-*")
                .size(1)
                .query(termQuery)
                .sort(sort -> sort
                        .field(f -> f
                                .field("@timestamp")
                                .order(SortOrder.Desc)
                        )
                )
        );

        SearchResponse<Map> response = esClient.search(searchRequest, Map.class);
        Hit<Map> firstHit = response.hits().hits().stream().findFirst().orElse(null);
        if (firstHit == null || firstHit.source() == null) {
            return null;
        }
        Object ts = firstHit.source().get("@timestamp");
        if (ts == null) {
            return null;
        }
        // 假设时间戳是 ISO-8601 字符串
        return Instant.from(DateTimeFormatter.ISO_INSTANT.parse(ts.toString()));
    }

    /**
     * 根据 appName + iamTreePath 从 Hera 拿到 ES 索引列表。
     * 这里先给出一个占位实现：按照约定规则拼一个索引前缀。
     * 你可以后续改为实际调用 Hera 服务。
     */
    protected List<String> findEsIndicesFromHera(String appName, String iamTreePath) {
        // 占位：简单根据 appName 生成一个索引模式，例如 "log-" + appName + "-*"
        String indexPattern = "log-" + appName + "-*";
        return List.of(indexPattern);
    }

    /**
     * 在指定索引中按时间窗口 + requestId（可选 level）查询日志。
     */
    protected List<Map<String, Object>> queryLogsInIndex(
            String index,
            String requestId,
            Instant from,
            Instant to,
            @Nullable String level,
            int size
    ) throws IOException {

        Query boolQuery = Query.of(q -> q
                .bool(b -> {
                    // must: requestId
                    b.must(m -> m
                            .term(t -> t
                                    .field("requestId")
                                    .value(requestId)
                            )
                    );
                    // filter: 时间范围
                    b.filter(f -> f
                            .range(r -> r
                                    .field("@timestamp")
                                    .gte(g -> g.date(DateTimeFormatter.ISO_INSTANT.format(from)))
                                    .lte(g -> g.date(DateTimeFormatter.ISO_INSTANT.format(to)))
                            )
                    );
                    // filter: level（可选）
                    if (level != null && !level.isBlank()) {
                        b.filter(f -> f
                                .term(t -> t
                                        .field("level")
                                        .value(level)
                                )
                        );
                    }
                    return b;
                })
        );

        final int finalSize = size <= 0 ? 100 : size;

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(index)
                .size(finalSize)
                .query(boolQuery)
                .sort(sort -> sort
                        .field(f -> f
                                .field("@timestamp")
                                .order(SortOrder.Desc)
                        )
                )
        );

        SearchResponse<Map> response = esClient.search(searchRequest, Map.class);

        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(src -> src != null)
                .map(src -> (Map<String, Object>) src)
                .collect(Collectors.toList());
    }
}

