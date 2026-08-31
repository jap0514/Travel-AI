package com.travel.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.entity.Hotel;
import com.travel.es.converter.HotelEsConverter;
import com.travel.es.doc.HotelDoc;
import com.travel.mapper.HotelMapper;
import com.travel.mapper.HotelRoomTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 酒店数据同步到 ES 的核心服务
 * <p>
 * 同步策略（实时性要求不高，定时增量 + 每日全量对账）：
 * <pre>
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  每 5 分钟（@Scheduled 触发）                              │
 *   │    ↓                                                     │
 *   │  incrementalSync()                                       │
 *   │    ├─ Redis 分布式锁（防集群重复执行）                     │
 *   │    ├─ 查 MySQL：hotel.update_time >= lastSyncTime        │
 *   │    ├─ 每条 hotel → 子查询 hotel_room_type MIN(price)      │
 *   │    ├─ bulk 写 ES                                          │
 *   │    └─ 推进 lastSyncTime 游标                              │
 *   └─────────────────────────────────────────────────────────┘
 *
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  每日 03:00（@Scheduled cron 触发）                       │
 *   │    ↓                                                     │
 *   │  fullSync()                                              │
 *   │    ├─ 分页全量写 ES                                       │
 *   │    ├─ 对账：ES 有但 MySQL 没有 → 物理删除                 │
 *   │    │        （替代 deleted 字段，最长 24h 延迟）           │
 *   │    └─ 重置 lastSyncTime                                  │
 *   └─────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @author travel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelSyncService {

    /** ES 索引名（与 hotel_v1.json mapping 对应）*/
    public static final String INDEX = "hotel_v1";

    /** Redis 游标 key：记录上次同步到的 update_time（毫秒）*/
    private static final String REDIS_KEY_LAST_SYNC = "es:sync:hotel:last_time";

    /** Redis 分布式锁 key：防止集群多实例重复执行 */
    private static final String REDIS_KEY_LOCK = "es:sync:hotel:lock";

    /** 分布式锁 TTL（分钟），异常崩溃后自动释放 */
    private static final long LOCK_TTL_MINUTES = 10;

    /** 首次启动时回溯窗口（小时），避免漏数据 */
    private static final long DEFAULT_BACK_HOURS = 1;

    /** 同步分页大小（查 MySQL 时使用）*/
    private static final int PAGE_SIZE = 500;

    /** 单次增量最多处理条数（防极端情况下 OOM）*/
    private static final int MAX_INCREMENT_PER_RUN = 2000;

    /** 时区 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private HotelMapper hotelMapper;

    @Autowired
    private HotelRoomTypeMapper hotelRoomTypeMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ElasticsearchClient esClient;

    // ====================================================================
    //  对外暴露的同步入口
    // ====================================================================

    /**
     * 增量同步：从 Redis 记录的游标开始，把变化的酒店同步到 ES
     *
     * @return 同步结果
     */
    public SyncResult incrementalSync() {
        if (!tryLock()) {
            log.info("[HotelSync] 增量同步：另一个实例正在执行，跳过");
            return SyncResult.skipped("another instance is running");
        }
        long start = System.currentTimeMillis();
        try {
            LocalDateTime lastSyncTime = getLastSyncTime();
            int totalIndexed = 0;
            int pageNum = 1;
            int offset = 0;

            // 分页拉取增量数据（hotel.update_time >= lastSyncTime）
            // 用手动 LIMIT/OFFSET 绕开 MyBatis Plus 全局分页限制 100 条
            while (totalIndexed < MAX_INCREMENT_PER_RUN) {
                List<Hotel> batch = fetchIncrementalByOffset(lastSyncTime, offset, PAGE_SIZE);
                if (batch.isEmpty()) {
                    break;
                }

                // 批量转 Doc + bulk 写 ES
                int indexed = bulkIndexFromHotels(batch);
                totalIndexed += indexed;

                log.info("[HotelSync] 增量 第 {} 页（offset={}），本批 {} 条，成功 {} 条，累计 {} 条",
                        pageNum, offset, batch.size(), indexed, totalIndexed);

                // 推进游标：本批最后一条的 update_time 作为新的 lastSyncTime
                LocalDateTime maxUpdate = batch.get(batch.size() - 1).getUpdateTime();
                if (maxUpdate != null) {
                    setLastSyncTime(maxUpdate);
                }

                if (batch.size() < PAGE_SIZE) break;
                offset += PAGE_SIZE;
                pageNum++;
            }

            long cost = System.currentTimeMillis() - start;
            log.info("[HotelSync] 增量同步完成，共处理 {} 条，耗时 {} ms", totalIndexed, cost);
            return SyncResult.ok(totalIndexed, 0, cost);
        } catch (Exception e) {
            log.error("[HotelSync] 增量同步异常", e);
            return SyncResult.error(e);
        } finally {
            unlock();
        }
    }

    /**
     * 全量同步：分页同步所有酒店 + 对账删除 ES 中已不存在的文档
     * <p>
     * 对账逻辑替代 hotel 表的 deleted 字段：
     * - MySQL 查所有 hotel_id → Set A
     * - ES scroll 查所有 _id → Set B
     * - B - A = ES 中已删除的（MySQL 已物理删除但 ES 还在）→ 删除
     */
    public SyncResult fullSync() {
        if (!tryLock()) {
            log.info("[HotelSync] 全量同步：另一个实例正在执行，跳过");
            return SyncResult.skipped("another instance is running");
        }
        long start = System.currentTimeMillis();
        try {
            // 1. 分页全量同步（用手动 LIMIT/OFFSET 绕开 MyBatis Plus 的 100 条分页限制）
            int indexed = 0;
            int offset = 0;
            int pageNum = 1;
            while (true) {
                List<Hotel> batch = fetchByOffset(offset, PAGE_SIZE);
                if (batch.isEmpty()) break;

                indexed += bulkIndexFromHotels(batch);
                log.info("[HotelSync] 全量 第 {} 页（offset={}），写入 {} 条", pageNum, offset, batch.size());

                if (batch.size() < PAGE_SIZE) break;
                offset += PAGE_SIZE;
                pageNum++;
            }
            log.info("[HotelSync] 全量索引完成，共写入 {} 条", indexed);

            // 2. 对账删除 ES 中已不存在的酒店（替代 deleted 字段）
            int deleted = reconcileDeletes();
            log.info("[HotelSync] 对账删除 {} 条", deleted);

            // 3. 重置游标为当前时间
            setLastSyncTime(LocalDateTime.now(ZONE));

            long cost = System.currentTimeMillis() - start;
            log.info("[HotelSync] 全量同步完成，写入 {} 条，删除 {} 条，耗时 {} ms", indexed, deleted, cost);
            return SyncResult.ok(indexed, deleted, cost);
        } catch (Exception e) {
            log.error("[HotelSync] 全量同步异常", e);
            return SyncResult.error(e);
        } finally {
            unlock();
        }
    }

    /**
     * 单条同步：用于实时性要求高的场景（如酒店信息被修改后立即同步）
     *
     * @param hotelId 酒店主键
     */
    public void syncOne(Long hotelId) {
        if (hotelId == null) return;
        try {
            Hotel hotel = hotelMapper.selectById(hotelId);
            if (hotel == null) {
                // MySQL 已删除 → 同步删除 ES 文档
                esClient.delete(d -> d.index(INDEX).id(String.valueOf(hotelId)));
                log.info("[HotelSync] 单条删除：hotelId={}", hotelId);
                return;
            }
            BigDecimal minPrice = hotelRoomTypeMapper.selectMinPriceByHotelId(hotelId);
            HotelDoc doc = HotelEsConverter.toDoc(hotel, minPrice);
            esClient.index(i -> i.index(INDEX).id(String.valueOf(hotelId)).document(doc));
            log.info("[HotelSync] 单条同步：hotelId={}", hotelId);
        } catch (Exception e) {
            log.error("[HotelSync] 单条同步失败：hotelId={}", hotelId, e);
        }
    }

    /**
     * 获取同步状态（管理后台展示用）
     */
    public SyncStatus getStatus() {
        SyncStatus status = new SyncStatus();
        status.setIndexName(INDEX);
        status.setLastSyncTime(getLastSyncTimeRaw());
        status.setMysqlTotal(hotelMapper.selectCount(null));
        try {
            status.setEsTotal(esClient.count(c -> c.index(INDEX)).count());
        } catch (Exception e) {
            status.setEsTotal(-1L);
            status.setError(e.getMessage());
        }
        return status;
    }

    // ====================================================================
    //  内部实现
    // ====================================================================

    /**
     * 把一批 Hotel 转成 HotelDoc 并 bulk 写入 ES
     */
    private int bulkIndexFromHotels(List<Hotel> hotels) throws Exception {
        if (hotels.isEmpty()) return 0;

        List<HotelDoc> docs = new ArrayList<>(hotels.size());
        for (Hotel hotel : hotels) {
            // 每条 hotel 单独查一次最低价（房型数量通常不多，子查询开销可接受）
            BigDecimal minPrice = hotelRoomTypeMapper.selectMinPriceByHotelId(hotel.getHotelId());
            docs.add(HotelEsConverter.toDoc(hotel, minPrice));
        }

        BulkRequest.Builder br = new BulkRequest.Builder();
        for (HotelDoc doc : docs) {
            br.operations(op -> op.index(idx -> idx
                    .index(INDEX)
                    .id(String.valueOf(doc.getHotelId()))
                    .document(doc)));
        }
        BulkResponse resp = esClient.bulk(br.build());

        int success = 0;
        for (BulkResponseItem item : resp.items()) {
            if (item.error() == null) {
                success++;
            } else {
                log.warn("[HotelSync] bulk 单条失败：id={}, reason={}",
                        item.id(), item.error().reason());
            }
        }
        return success;
    }

    /**
     * 手动 LIMIT/OFFSET 全量拉取（绕开 MyBatis Plus 全局分页 100 条限制）
     * <p>
     * 用 {@code last("LIMIT offset, size")} 注入原生 SQL 绕过 PaginationInnerInterceptor
     *
     * @param offset 起始偏移
     * @param size   每批大小
     * @return 酒店列表（按 hotel_id 升序）
     */
    private List<Hotel> fetchByOffset(int offset, int size) {
        return hotelMapper.selectList(
                new LambdaQueryWrapper<Hotel>()
                        .orderByAsc(Hotel::getHotelId)
                        .last("LIMIT " + offset + ", " + size));
    }

    /**
     * 手动 LIMIT/OFFSET 增量拉取（按 update_time 过滤）
     *
     * @param lastSyncTime 起始时间（update_time >= 此值）
     * @param offset       偏移
     * @param size         每批大小
     * @return 增量酒店列表
     */
    private List<Hotel> fetchIncrementalByOffset(LocalDateTime lastSyncTime, int offset, int size) {
        return hotelMapper.selectList(
                new LambdaQueryWrapper<Hotel>()
                        .ge(lastSyncTime != null, Hotel::getUpdateTime, lastSyncTime)
                        .orderByAsc(Hotel::getUpdateTime)
                        .last("LIMIT " + offset + ", " + size));
    }

    /**
     * 对账：删除 ES 中 MySQL 已不存在的酒店（替代 deleted 字段）
     */
    private int reconcileDeletes() throws Exception {
        // 1. 收集 MySQL 所有 hotel_id（用 selectObjs 只查 ID 列，省内存）
        Set<Long> mysqlIds = new HashSet<>();
        List<Object> ids = hotelMapper.selectObjs(
                new QueryWrapper<Hotel>().select("hotel_id"));
        for (Object id : ids) {
            if (id != null) mysqlIds.add(Long.parseLong(id.toString()));
        }

        // 2. scroll 收集 ES 所有 _id
        Set<String> esIds = new HashSet<>();
        SearchResponse<HotelDoc> initialResp = esClient.search(s -> s
                .index(INDEX)
                .size(1000)
                .scroll(t -> t.time("2m"))
                .query(q -> q.matchAll(m -> m)), HotelDoc.class);

        // scrollId 会被循环重新赋值，用 String[1] 包装以便 lambda 捕获
        final String[] scrollIdHolder = new String[]{ initialResp.scrollId() };
        List<Hit<HotelDoc>> hits = initialResp.hits().hits();
        while (!hits.isEmpty()) {
            for (Hit<HotelDoc> hit : hits) {
                esIds.add(hit.id());
            }
            // 后续 scroll 返回 ScrollResponse<HotelDoc>
            co.elastic.clients.elasticsearch.core.ScrollResponse<HotelDoc> scrollResp =
                    esClient.scroll(s -> s.scrollId(scrollIdHolder[0]).scroll(t -> t.time("2m")), HotelDoc.class);
            scrollIdHolder[0] = scrollResp.scrollId();
            hits = scrollResp.hits().hits();
        }
        // 清理 scroll 上下文
        try {
            esClient.clearScroll(c -> c.scrollId(scrollIdHolder[0]));
        } catch (Exception ignored) { }

        // 3. 找出 ES 有但 MySQL 没有的（已删除的酒店）
        Set<String> toDelete = new HashSet<>();
        for (String esId : esIds) {
            try {
                if (!mysqlIds.contains(Long.parseLong(esId))) {
                    toDelete.add(esId);
                }
            } catch (NumberFormatException ignored) { }
        }

        // 4. 批量删除
        if (toDelete.isEmpty()) return 0;

        BulkRequest.Builder br = new BulkRequest.Builder();
        toDelete.forEach(id -> br.operations(op -> op.delete(d -> d.index(INDEX).id(id))));
        BulkResponse respDel = esClient.bulk(br.build());

        int deleted = 0;
        for (BulkResponseItem item : respDel.items()) {
            // ES 8.x 客户端：result() 返回 String 而非枚举
            String result = item.result();
            if ("deleted".equals(result) || "not_found".equals(result)) {
                deleted++;
            }
        }
        return deleted;
    }

    // ====================================================================
    //  Redis 分布式锁
    // ====================================================================

    /**
     * 尝试获取分布式锁（SET NX + TTL）
     */
    private boolean tryLock() {
        Boolean ok = redis.opsForValue().setIfAbsent(
                REDIS_KEY_LOCK, "1", LOCK_TTL_MINUTES, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放分布式锁
     */
    private void unlock() {
        try {
            redis.delete(REDIS_KEY_LOCK);
        } catch (Exception e) {
            log.warn("[HotelSync] 释放分布式锁失败", e);
        }
    }

    // ====================================================================
    //  Redis 游标（lastSyncTime）
    // ====================================================================

    /**
     * 读取游标：上次同步到的 update_time
     * <p>
     * 首次调用（key 不存在）→ 返回当前时间往前 1 小时，避免漏数据
     */
    private LocalDateTime getLastSyncTime() {
        String raw = redis.opsForValue().get(REDIS_KEY_LAST_SYNC);
        if (raw == null || raw.isBlank()) {
            log.info("[HotelSync] 首次同步，lastSyncTime 默认为当前 -1h");
            return LocalDateTime.now(ZONE).minusHours(DEFAULT_BACK_HOURS);
        }
        try {
            long millis = Long.parseLong(raw);
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZONE);
        } catch (Exception e) {
            log.warn("[HotelSync] 解析 lastSyncTime 失败，fallback 到默认窗口", e);
            return LocalDateTime.now(ZONE).minusHours(DEFAULT_BACK_HOURS);
        }
    }

    /**
     * 读取游标的原始字符串（用于状态展示）
     */
    private String getLastSyncTimeRaw() {
        String raw = redis.opsForValue().get(REDIS_KEY_LAST_SYNC);
        if (raw == null || raw.isBlank()) return null;
        try {
            long millis = Long.parseLong(raw);
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZONE)
                    .toString().replace('T', ' ');
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * 写入游标（存为毫秒时间戳，跨时区一致）
     */
    private void setLastSyncTime(LocalDateTime time) {
        if (time == null) return;
        long millis = time.atZone(ZONE).toInstant().toEpochMilli();
        redis.opsForValue().set(REDIS_KEY_LAST_SYNC, String.valueOf(millis));
    }

    // ====================================================================
    //  DTO
    // ====================================================================

    /** 同步结果 */
    @lombok.Data
    public static class SyncResult {
        private boolean success;
        private int indexed;
        private int deleted;
        private long costMillis;
        private String message;
        private String error;

        public static SyncResult ok(int indexed, int deleted, long cost) {
            SyncResult r = new SyncResult();
            r.success = true;
            r.indexed = indexed;
            r.deleted = deleted;
            r.costMillis = cost;
            r.message = "OK";
            return r;
        }

        public static SyncResult skipped(String reason) {
            SyncResult r = new SyncResult();
            r.success = true;
            r.message = "skipped: " + reason;
            return r;
        }

        public static SyncResult error(Throwable t) {
            SyncResult r = new SyncResult();
            r.success = false;
            r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
            return r;
        }
    }

    /** 同步状态 */
    @lombok.Data
    public static class SyncStatus {
        private String indexName;
        private String lastSyncTime;
        private Long mysqlTotal;
        private Long esTotal;
        private String error;
    }
}
