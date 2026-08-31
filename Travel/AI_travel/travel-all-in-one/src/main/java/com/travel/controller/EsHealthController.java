package com.travel.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Elasticsearch 健康检查接口（Controller）
 * <p>
 * 提供两个调试接口用于验证 ES 客户端是否成功联通：
 * <ul>
 *   <li>GET /es/health      —— 检查集群状态（节点数、活跃分片等）</li>
 *   <li>GET /es/ping         —— 最简单的连通性测试</li>
 * </ul>
 * <p>
 * 仅用于开发/调试阶段验证 ES Bean 是否装配成功，
 * 生产环境建议移除或通过 Spring Actuator 的 ElasticSearchHealthIndicator 替代。
 *
 * @author travel
 */
@Slf4j
@RestController
@RequestMapping("/es")
public class EsHealthController {

    /**
     * 官方 ES Java Client，由 ElasticsearchConfig 注册的 Bean
     */
    @Autowired
    private ElasticsearchClient esClient;

    /**
     * 集群健康检查
     * <p>
     * 调用 ES Cluster Health API，返回：
     * <ul>
     *   <li>status: UP / DOWN（本服务视角的连通性）</li>
     *   <li>clusterStatus: green / yellow / red（ES 集群自身状态）</li>
     *   <li>numberOfNodes: 集群节点数</li>
     *   <li>activeShards: 活跃分片数</li>
     * </ul>
     *
     * @return 健康检查结果 Map
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        // 使用 LinkedHashMap 保证返回 JSON 字段顺序与声明一致
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            // 调用 ES 集群健康 API（无参数查询）
            HealthResponse resp = esClient.cluster().health(h -> h);

            // 业务层"连通性"状态：本次调用成功即为 UP
            result.put("status", "UP");
            // ES 集群自身健康度
            result.put("clusterName", resp.clusterName());
            result.put("clusterStatus", resp.status().jsonValue());
            result.put("numberOfNodes", resp.numberOfNodes());
            result.put("activeShards", resp.activeShards());

            log.info("[ES Health] OK，集群={}，状态={}，节点数={}",
                    resp.clusterName(), resp.status(), resp.numberOfNodes());
        } catch (Exception e) {
            // ES 未启动、网络不通、鉴权失败等都会走到这里
            log.error("[ES Health] 连接失败", e);
            result.put("status", "DOWN");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * 最简连通性测试
     * <p>
     * 调用 ES Ping API（HEAD /），不返回任何业务数据，
     * 仅验证 TCP+HTTP 链路是否通。性能开销极小，适合用作存活探针。
     *
     * @return 包含 ping 状态的 Map
     */
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // ping() 返回 BooleanResponse，成功返回 true
            Boolean ok = esClient.ping().value();
            result.put("status", ok ? "UP" : "DOWN");
            log.info("[ES Ping] {}", ok ? "成功" : "失败");
        } catch (Exception e) {
            log.error("[ES Ping] 失败", e);
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }
}
