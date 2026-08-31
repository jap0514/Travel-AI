package com.travel.scheduler;

import com.travel.service.impl.HotelSyncService;
import com.travel.service.impl.HotelSyncService.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 酒店数据同步定时调度器
 * <p>
 * 调度规则：
 * <ul>
 *   <li>增量同步：每 5 分钟一次，应用启动后延迟 60 秒首次执行</li>
 *   <li>全量对账：每天凌晨 03:00 一次</li>
 * </ul>
 * <p>
 * 启用条件：在 Spring Boot 启动类上加 {@code @EnableScheduling}
 *
 * @author travel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelSyncScheduler {

    private final HotelSyncService hotelSyncService;

    /**
     * 增量同步：每 5 分钟执行一次
     * <p>
     * fixedDelay 策略：上次执行结束后再等 5 分钟（避免上一次未跑完就启动下一次）
     * initialDelay：应用启动后 60 秒再首次执行（等 ES 客户端就绪、连接池预热）
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void incremental() {
        try {
            SyncResult result = hotelSyncService.incrementalSync();
            log.info("[HotelSync-Scheduler] 增量调度完成：success={}, indexed={}, cost={}ms, msg={}",
                    result.isSuccess(), result.getIndexed(), result.getCostMillis(), result.getMessage());
        } catch (Exception e) {
            // 防御性捕获：单个任务异常不影响 Spring 调度框架
            log.error("[HotelSync-Scheduler] 增量调度异常", e);
        }
    }

    /**
     * 全量对账同步：每天凌晨 03:00 执行
     * <p>
     * cron 表达式：秒 分 时 日 月 周
     * <ul>
     *   <li>0 秒</li>
     *   <li>0 分</li>
     *   <li>3 时（凌晨 3 点）</li>
     *   <li>* 日</li>
     *   <li>* 月</li>
     *   <li>? 周（与日字段互斥，必须有一个为 ?）</li>
     * </ul>
     * <p>
     * 全量同步会做对账删除（替代 deleted 字段），最长 24 小时延迟清理已删除酒店。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyFullSync() {
        try {
            SyncResult result = hotelSyncService.fullSync();
            log.info("[HotelSync-Scheduler] 全量调度完成：success={}, indexed={}, deleted={}, cost={}ms",
                    result.isSuccess(), result.getIndexed(), result.getDeleted(), result.getCostMillis());
        } catch (Exception e) {
            log.error("[HotelSync-Scheduler] 全量调度异常", e);
        }
    }
}
