package com.travel.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.entity.HotelBooking;
import com.travel.mapper.HotelBookingMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时自动取消处理器
 * 使用 Redisson 延迟队列实现
 */
@Slf4j
@Component
public class OrderTimeoutProcessor {

    public static final String QUEUE_NAME = "order:timeout:queue";

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private HotelBookingMapper hotelBookingMapper;

    @PostConstruct
    public void init() {
        // 启动后台线程监听延迟队列
        new Thread(this::processQueue).start();
        log.info("订单超时处理器已启动");
    }

    private void processQueue() {
        try {
            // 获取阻塞队列引用
            RBlockingQueue<String> queue = redissonClient.getBlockingQueue(QUEUE_NAME);

            // 获取延迟队列（基于阻塞队列）
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(queue);

            // 不断从队列中取值（阻塞等待，有值来才返回）
            while (true) {
                String orderNo = queue.take();
                log.info("处理超时订单: {}", orderNo);
                processTimeoutOrder(orderNo);
            }
        } catch (Exception e) {
            log.error("处理超时订单异常", e);
            // 发生异常后稍等再重试
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            // 重新启动处理
            processQueue();
        }
    }

    private void processTimeoutOrder(String orderNo) {
        // 查询订单
        LambdaQueryWrapper<HotelBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotelBooking::getOrderNo, orderNo);
        HotelBooking booking = hotelBookingMapper.selectOne(wrapper);

        if (booking == null) {
            log.warn("订单不存在: {}", orderNo);
            return;
        }

        // 只有待支付的订单才需要取消
        if (booking.getStatus() != 0) {
            log.info("订单状态不是待支付，跳过: {}, status={}", orderNo, booking.getStatus());
            return;
        }

        // 取消订单
        booking.setStatus(3); // 已取消
        booking.setCancelReason("支付超时自动取消");
        booking.setCancelTime(LocalDateTime.now());
        booking.setUpdateTime(LocalDateTime.now());
        hotelBookingMapper.updateById(booking);

        log.info("订单已自动取消: {}", orderNo);
    }
}
