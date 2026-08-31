package com.travel.controller;

import com.travel.service.impl.HotelSyncService;
import com.travel.service.impl.HotelSyncService.SyncResult;
import com.travel.service.impl.HotelSyncService.SyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 酒店 ES 同步管理接口（仅用于运维和调试）
 * <p>
 * 提供 4 个接口：
 * <ul>
 *   <li>POST /admin/hotel/reindex      —— 触发全量同步（含对账删除）</li>
 *   <li>POST /admin/hotel/syncOne/{id} —— 同步指定酒店</li>
 *   <li>POST /admin/hotel/syncIncremental —— 手动触发增量同步</li>
 *   <li>GET  /admin/hotel/syncStatus   —— 查看同步状态</li>
 * </ul>
 *
 * @author travel
 */
@Slf4j
@RestController
@RequestMapping("/admin/hotel")
@RequiredArgsConstructor
public class HotelAdminController {

    private final HotelSyncService hotelSyncService;

    /**
     * 触发全量同步
     * <p>
     * 用法：POST /admin/hotel/reindex
     * <p>
     * 内部流程：分页全量写入 → 对账删除 ES 中已不存在的酒店（替代 deleted 字段）
     * 数据量大时（万级以上）可能耗时较长，建议观察日志。
     */
    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        log.info("[HotelAdmin] 收到全量同步请求");
        long start = System.currentTimeMillis();
        SyncResult result = hotelSyncService.fullSync();
        return buildResponse(result, start);
    }

    /**
     * 同步单个酒店
     * <p>
     * 用法：POST /admin/hotel/syncOne/12345
     * <p>
     * 适用场景：酒店信息修改后想立即同步（不等 5 分钟增量）
     */
    @PostMapping("/syncOne/{hotelId}")
    public Map<String, Object> syncOne(@PathVariable Long hotelId) {
        log.info("[HotelAdmin] 收到单条同步请求：hotelId={}", hotelId);
        hotelSyncService.syncOne(hotelId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "已提交单条同步：hotelId=" + hotelId);
        return resp;
    }

    /**
     * 手动触发增量同步
     * <p>
     * 用法：POST /admin/hotel/syncIncremental
     * <p>
     * 适用场景：调试时不等 5 分钟 cron，立即跑一次增量
     */
    @PostMapping("/syncIncremental")
    public Map<String, Object> syncIncremental() {
        log.info("[HotelAdmin] 收到增量同步请求");
        long start = System.currentTimeMillis();
        SyncResult result = hotelSyncService.incrementalSync();
        return buildResponse(result, start);
    }

    /**
     * 查看同步状态
     * <p>
     * 用法：GET /admin/hotel/syncStatus
     * <p>
     * 返回：索引名 / 上次同步时间 / MySQL 总数 / ES 总数 / 错误信息
     */
    @GetMapping("/syncStatus")
    public Map<String, Object> syncStatus() {
        SyncStatus status = hotelSyncService.getStatus();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("indexName", status.getIndexName());
        resp.put("lastSyncTime", status.getLastSyncTime());
        resp.put("mysqlTotal", status.getMysqlTotal());
        resp.put("esTotal", status.getEsTotal());
        resp.put("diff", status.getMysqlTotal() != null && status.getEsTotal() != null
                ? status.getMysqlTotal() - status.getEsTotal() : null);
        if (status.getError() != null) {
            resp.put("error", status.getError());
        }
        return resp;
    }

    /**
     * 构造统一的响应格式
     */
    private Map<String, Object> buildResponse(SyncResult result, long start) {
        long totalCost = System.currentTimeMillis() - start;
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", result.isSuccess());
        resp.put("indexed", result.getIndexed());
        resp.put("deleted", result.getDeleted());
        resp.put("syncCostMillis", result.getCostMillis());
        resp.put("totalCostMillis", totalCost);
        resp.put("message", result.getMessage());
        if (result.getError() != null) {
            resp.put("error", result.getError());
        }
        return resp;
    }
}
