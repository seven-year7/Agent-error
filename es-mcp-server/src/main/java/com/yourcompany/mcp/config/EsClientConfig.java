package com.yourcompany.mcp.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 客户端配置。
 * 使用官方 elasticsearch-java 客户端，通过 HTTP 与 ES 通信。
 */
@Configuration
public class EsClientConfig {

    @Value("${es.host:localhost}")
    private String esHost;

    @Value("${es.port:9200}")
    private int esPort;

    @Value("${es.scheme:http}")
    private String esScheme;

    @Bean
    public RestClient restClient() {
        return RestClient.builder(new HttpHost(esHost, esPort, esScheme)).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}

