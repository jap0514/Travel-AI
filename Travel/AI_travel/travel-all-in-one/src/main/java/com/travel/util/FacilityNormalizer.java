package com.travel.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 酒店设施 JSON 归一化工具
 * <p>
 * 数据约定：hotel.facilities 字段统一为 JSON 数组格式：
 * <pre>
 *   ["游泳池", "健身房", "WiFi", "SPA", "24小时前台"]
 * </pre>
 * <p>
 * 处理规则：
 * <ul>
 *   <li>null / 空字符串 / 空白 → 返回空 List</li>
 *   <li>JSON 数组 → 直接转为 List&lt;String&gt;</li>
 *   <li>JSON 解析失败 → 记录日志并返回空 List（不让同步任务崩溃）</li>
 *   <li>非数组形态（防御）→ 记 warn 日志并返回空 List</li>
 * </ul>
 *
 * @author travel
 */
@Slf4j
public final class FacilityNormalizer {

    /** Jackson 实例（线程安全，复用）*/
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FacilityNormalizer() {
        // 工具类不允许实例化
    }

    /**
     * 把 JSON 数组字符串转为 List&lt;String&gt;
     *
     * @param rawJson MySQL hotel.facilities 字段值，期望格式：["WiFi","游泳池"]
     * @return 归一化后的设施列表，永不为 null
     */
    public static List<String> normalize(String rawJson) {
        // 1. 防御：null / 空字符串 / 空白
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = MAPPER.readTree(rawJson);

            // 2. 仅支持数组形态
            if (root.isArray()) {
                List<String> out = new ArrayList<>(root.size());
                root.forEach(n -> {
                    String s = n.asText();
                    if (s != null && !s.isBlank()) {
                        out.add(s.trim());
                    }
                });
                return out;
            }

            // 3. 非数组形态（防御性日志，实际不应出现）
            log.warn("facilities JSON 期望数组形态，实际类型={}，原值=[{}]",
                    root.getNodeType(), rawJson);
            return Collections.emptyList();
        } catch (Exception e) {
            // JSON 解析失败时不让同步任务崩溃
            log.warn("facilities JSON 解析失败，原文=[{}]，错误：{}", rawJson, e.getMessage());
            return Collections.emptyList();
        }
    }
}
