package com.travel.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elasticsearch 客户端 Bean 注册配置
 * <p>
 * 本类负责按以下层次组装 ES 客户端，并注册为 Spring Bean：
 * <pre>
 *   RestClient (Apache HttpClient 底层)
 *     └── ElasticsearchTransport (JSON 序列化 + 请求路由)
 *           └── ElasticsearchClient (官方 Java API Client，业务层调用入口)
 * </pre>
 * <p>
 * 业务层只需 @Autowired ElasticsearchClient 即可调用所有 ES API，
 * 例如：esClient.search(...) / esClient.index(...) / esClient.bulk(...)。
 *
 * @author travel
 */
@Slf4j
@Configuration
public class ElasticsearchConfig {

    /**
     * ES 连接配置（来自 application.yml）
     */
    @Autowired
    private ElasticsearchProperties properties;

    /**
     * 构造底层 Apache HTTP RestClient
     * <p>
     * 负责 HTTP 连接池、超时配置、SSL、认证等底层网络通信。
     * 注册为 Bean 时指定 destroyMethod="close"，应用关闭时自动释放连接池。
     *
     * @return 配置好的 RestClient 实例
     */
    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        // 1. 解析 uris 字符串 → HttpHost 列表（支持多节点集群）
        List<HttpHost> hosts = parseUris(properties.getUris());
        log.info("[ES] 初始化 RestClient，节点列表：{}", hosts);

        // 2. 构建 RestClient，设置连接超时与 Socket 超时
        RestClient client = RestClient.builder(hosts.toArray(new HttpHost[0]))
                .setRequestConfigCallback(rcb -> rcb
                        // TCP 三次握手超时
                        .setConnectTimeout((int) parseMillis(properties.getConnectionTimeout()))
                        // 请求发出后等待响应的超时（避免长时间阻塞）
                        .setSocketTimeout((int) parseMillis(properties.getSocketTimeout())))
                .build();

        return client;
    }

    /**
     * 构造 ES 传输层 Transport
     * <p>
     * Transport 负责：
     * <ul>
     *   <li>把 Java 对象序列化为 JSON 发送给 ES（JacksonJsonpMapper 基于 Jackson）</li>
     *   <li>把 ES 返回的 JSON 反序列化为 Java 对象</li>
     *   <li>底层调用 RestClient 发送 HTTP 请求</li>
     * </ul>
     *
     * @param restClient 由 elasticsearchRestClient() Bean 注入
     * @return ElasticsearchTransport 实例
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        // 自定义 ObjectMapper，注册 JSR310 模块支持 LocalDateTime/LocalDate
        // 默认的 JacksonJsonpMapper 创建的 ObjectMapper 不包含 JSR310，会导致
        // LocalDateTime 字段序列化失败（典型报错：Java 8 date/time type not supported）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // 禁用时间戳格式，让 @JsonFormat 注解生效（输出 ISO8601 字符串）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    /**
     * 构造官方 ElasticsearchClient（业务层入口）
     * <p>
     * ElasticsearchClient 提供强类型的 Fluent API，例如：
     * <pre>
     *   esClient.search(s -> s.index("hotel_v1").query(...), HotelDoc.class);
     *   esClient.index(i -> i.index("hotel_v1").id("1").document(hotelDoc));
     *   esClient.bulk(b -> b.operations(...));
     * </pre>
     *
     * @param transport 由 elasticsearchTransport() Bean 注入
     * @return ElasticsearchClient 实例，业务层 @Autowired 即可使用
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        ElasticsearchClient client = new ElasticsearchClient(transport);
        log.info("[ES] ElasticsearchClient Bean 创建成功，业务层可 @Autowired 注入");
        return client;
    }

    // ============================================================
    //  私有工具方法
    // ============================================================

    /**
     * 将 application.yml 中的 uris 字符串解析为 HttpHost 列表
     * <p>
     * 支持格式：
     * <ul>
     *   <li>单节点：http://host:9200</li>
     *   <li>集群：http://host1:9200,http://host2:9200</li>
     *   <li>无端口：默认 9200</li>
     *   <li>无 scheme：默认 http</li>
     * </ul>
     *
     * @param uris 逗号分隔的 URI 字符串
     * @return HttpHost 列表
     * @throws IllegalArgumentException URI 格式不合法时抛出
     */
    private List<HttpHost> parseUris(String uris) {
        return Arrays.stream(uris.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::toHttpHost)
                .collect(Collectors.toList());
    }

    /**
     * 单个 URI 字符串 → HttpHost 转换
     *
     * @param s URI 字符串，例如 http://192.168.71.140:9200
     * @return HttpHost 对象
     */
    private HttpHost toHttpHost(String s) {
        try {
            URI uri = new URI(s);
            // scheme 缺省时默认 http
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            // port 缺省时默认 9200
            int port = uri.getPort() == -1 ? 9200 : uri.getPort();
            return new HttpHost(uri.getHost(), port, scheme);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("[ES] URI 格式错误: " + s, e);
        }
    }

    /**
     * 把带单位的时长字符串转换为毫秒数
     * <p>
     * 示例：
     * <ul>
     *   <li>"5s" → 5000</li>
     *   <li>"1m" → 60000</li>
     *   <li>"500" → 500（无单位，按毫秒处理）</li>
     * </ul>
     *
     * @param duration 时长字符串
     * @return 毫秒数
     */
    private long parseMillis(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 5000L;  // 默认 5 秒
        }

        char unit = duration.charAt(duration.length() - 1);
        long value = Long.parseLong(duration.substring(0, duration.length() - 1));

        // 根据末尾单位换算成毫秒
        return switch (unit) {
            case 's' -> value * 1000L;        // 秒
            case 'm' -> value * 60 * 1000L;   // 分
            default  -> Long.parseLong(duration);  // 无单位按毫秒
        };
    }
}
