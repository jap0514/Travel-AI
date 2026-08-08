package com.travel.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 布隆过滤器工具类
 * 用于解决缓存穿透问题
 */
@Slf4j
@Component
public class BloomFilterUtil {

    /**
     * 布隆过滤器实例
     * 使用 Google Guava 实现
     */
    private BloomFilter<String> hotelBloomFilter;

    /**
     * 布隆过滤器配置参数
     * - 预期插入数量：10000（可容纳的酒店数量）
     * - 误判率：0.01（1%）
     */
    private static final long EXPECTED_INSERTIONS = 10000;
    private static final double FPP = 0.01;

    @PostConstruct  //依赖注入后立即执行
    public void init() {
        // 初始化酒店查询的布隆过滤器
        hotelBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );
        log.info("布隆过滤器初始化完成，预期插入数量={}，误判率={}", EXPECTED_INSERTIONS, FPP);
    }

    /**
     * 判断 key 是否可能存在于布隆过滤器中
     *
     * @param cacheName 缓存名称（如 "hotels"）
     * @param key       缓存 key
     * @return true = 可能存在（需要继续查缓存/DB），false = 一定不存在（直接返回）
     */
    public boolean mightContain(String cacheName, String key) {
        switch (cacheName) {
            case "hotels":
                return hotelBloomFilter.mightContain(key);
            default:
                // 未知缓存类型，默认放行
                return true;
        }
    }

    /**
     * 向布隆过滤器中添加 key
     * 在缓存被写入时调用
     *
     * @param cacheName 缓存名称
     * @param key       缓存 key
     */
    public void put(String cacheName, String key) {
        switch (cacheName) {
            case "hotels":
                hotelBloomFilter.put(key);
                break;
            default:
                // 未知缓存类型，忽略
                break;
        }
    }

    /**
     * 批量添加 key 到布隆过滤器
     * 用于启动时预热热点数据
     *
     * @param cacheName 缓存名称
     * @param keys      key 集合
     */
    public void putAll(String cacheName, Iterable<String> keys) {
        switch (cacheName) {
            case "hotels":
                keys.forEach(hotelBloomFilter::put);
                break;
            default:
                break;
        }
    }

    /**
     * 获取布隆过滤器的统计信息
     * 用于监控和调优
     */
    public String getStatus() {
        if (hotelBloomFilter == null) {
            return "BloomFilter not initialized";
        }
        return String.format("expectedInsertions=%d, fpp=%f, size=%d",
                EXPECTED_INSERTIONS, FPP, hotelBloomFilter.approximateElementCount());
    }
}
