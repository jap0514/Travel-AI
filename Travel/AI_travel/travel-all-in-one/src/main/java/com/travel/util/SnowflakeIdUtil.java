package com.travel.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器（基于 Hutool）
 * 生成全局唯一、趋势递增的数字ID，适合作为 traceId
 */
@Slf4j
public class SnowflakeIdUtil {

    private static Snowflake snowflake;

    // 静态初始化：从配置或系统属性中读取 workerId 和 datacenterId
    static {
        long workerId = getWorkerId();       // 机器标识（工作节点）
        long datacenterId = getDataCenterId(); // 数据中心标识
        snowflake = IdUtil.getSnowflake(workerId, datacenterId);
        log.info("雪花算法工具初始化完成，workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /**
     * 获取下一个唯一ID（字符串形式）
     */
    public static String nextId() {
        return String.valueOf(snowflake.nextId());
    }

    /**
     * 获取下一个唯一ID（long类型）
     */
    public static long nextLong() {
        return snowflake.nextId();
    }

    // ======================== workerId & datacenterId 获取策略 ========================
    // 建议以下两种方式二选一，根据实际环境调整

    /**
     * 方式1：从系统属性或环境变量读取（启动时指定）
     * 例如：-DworkerId=1
     */
    private static long getWorkerId() {
        // 优先从 JVM 参数获取
        String workerIdStr = System.getProperty("workerId");
        if (workerIdStr != null) {
            try {
                return Long.parseLong(workerIdStr);
            } catch (NumberFormatException e) {
                log.warn("解析 workerId 失败，使用默认策略", e);
            }
        }
        // 备用：使用 IPv4 地址的最后一段（范围 0-255，多个实例可能冲突）
        long ipSuffix = NetUtil.getLocalhostStr().split("\\.")[3].hashCode() & 0x1F; // 取低5位 0-31
        return ipSuffix;
    }

    private static long getDataCenterId() {
        // 优先从 JVM 参数获取
        String dcIdStr = System.getProperty("datacenterId");
        if (dcIdStr != null) {
            try {
                return Long.parseLong(dcIdStr);
            } catch (NumberFormatException e) {
                log.warn("解析 datacenterId 失败，使用默认策略", e);
            }
        }
        // 备用：使用主机名 hash 取模 0-31
        String hostname = NetUtil.getLocalHostName();
        return (hostname.hashCode() & 0x1F);
    }
}