package com.travel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 连接配置属性类
 * <p>
 * 用于绑定 application.yml 中以 "elasticsearch:" 开头的配置项。
 * 通过 @ConfigurationProperties(prefix = "elasticsearch") 自动注入，
 * 配合 @Component 注册为 Spring Bean，可在其他配置类中 @Autowired 注入使用。
 * <p>
 * 示例配置（application.yml）：
 * <pre>
 * elasticsearch:
 *   uris: http://192.168.71.140:9200
 *   connection-timeout: 5s
 *   socket-timeout: 30s
 *   max-retries: 3
 * </pre>
 *
 * @author travel
 */
@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    /**
     * ES 服务地址
     * <p>
     * 单节点示例：http://192.168.71.140:9200
     * 集群示例：http://node1:9200,http://node2:9200,http://node3:9200
     * 默认值：本地 http://localhost:9200
     */
    private String uris = "http://192.168.71.140:9200";

    /**
     * TCP 连接建立超时时间
     * <p>
     * 支持单位：s（秒）/ m（分），如 "5s"、"1m"
     * 默认：5 秒
     */
    private String connectionTimeout = "5s";

    /**
     * Socket 读写超时时间（请求发出后等待响应的最长时间）
     * <p>
     * 支持单位：s（秒）/ m（分），如 "30s"、"2m"
     * 默认：30 秒。批量 bulk 操作建议调大到 60s 以上。
     */
    private String socketTimeout = "30s";

    /**
     * HTTP Basic 鉴权用户名
     * <p>
     * 仅当 ES 开启了 xpack.security.enabled=true 时需要填写。
     * 演示阶段关闭鉴权（false），可保持为空。
     */
    private String username;

    /**
     * HTTP Basic 鉴权密码
     * <p>
     * 仅当 ES 开启了 xpack.security.enabled=true 时需要填写。
     * 生产环境建议从环境变量或配置中心读取，不要硬编码。
     */
    private String password;

    /**
     * 单次请求失败时的最大重试次数
     * <p>
     * 仅对幂等操作（如 search、get）自动重试；非幂等操作（index、delete）不重试。
     * 默认：3 次
     */
    private int maxRetries = 3;
}
