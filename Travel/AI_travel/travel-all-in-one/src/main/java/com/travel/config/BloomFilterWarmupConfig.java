package com.travel.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.entity.Hotel;
import com.travel.entity.HotelRoomType;
import com.travel.mapper.HotelMapper;
import com.travel.mapper.HotelRoomTypeMapper;
import com.travel.util.BloomFilterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 布隆过滤器预热配置
 *
 * 在应用启动时自动执行：
 * 1. 查询所有酒店城市，添加到布隆过滤器
 * 2. 查询所有房型，添加到布隆过滤器
 *
 * 这样查询时布隆过滤器不会误拦截
 */
@Component
@Slf4j
public class BloomFilterWarmupConfig {

    @Autowired
    private HotelMapper hotelMapper;

    @Autowired
    private HotelRoomTypeMapper hotelRoomTypeMapper;

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    /**
     * 启动时预热布隆过滤器
     */
    @PostConstruct
    public void warmupBloomFilter() {
        try {
            log.info("【布隆过滤器预热】开始预热...");

            // 预热酒店城市
            warmupHotelCities();

            // 预热房型
            warmupRoomTypes();

            log.info("【布隆过滤器预热】预热完成");

        } catch (Exception e) {
            log.error("【布隆过滤器预热】预热失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 预热酒店城市到布隆过滤器
     */
    private void warmupHotelCities() {
        List<Hotel> hotels = hotelMapper.selectList(null);

        if (hotels == null || hotels.isEmpty()) {
            log.warn("【布隆过滤器预热】未查询到酒店数据");
            return;
        }

        // 使用 Set 去重，避免同一城市添加多次
        Set<String> cities = new HashSet<>();
        for (Hotel hotel : hotels) {
            if (hotel.getCity() != null && !hotel.getCity().isBlank()) {
                cities.add(hotel.getCity());
            }
        }

        // 添加到布隆过滤器
        for (String city : cities) {
            String fullKey = "hotels:" + city;
            bloomFilterUtil.put("hotels", fullKey);
        }

        log.info("【布隆过滤器预热】酒店城市预热完成，共 {} 个城市", cities.size());
    }

    /**
     * 预热房型到布隆过滤器
     */
    private void warmupRoomTypes() {
        List<HotelRoomType> roomTypes = hotelRoomTypeMapper.selectList(null);

        if (roomTypes == null || roomTypes.isEmpty()) {
            log.warn("【布隆过滤器预热】未查询到房型数据");
            return;
        }

        for (HotelRoomType roomType : roomTypes) {
            if (roomType.getHotelId() != null) {
                String fullKey = "roomTypes:" + roomType.getHotelId();
                bloomFilterUtil.put("roomTypes", fullKey);
            }
        }

        log.info("【布隆过滤器预热】房型预热完成，共 {} 个", roomTypes.size());
    }
}
