package com.travel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.annotation.ThreeTierCache;
import com.travel.dto.HotelAdminDTO;
import com.travel.entity.Hotel;
import com.travel.entity.HotelRoom;
import com.travel.entity.HotelRoomType;
import com.travel.mapper.HotelRoomMapper;
import com.travel.mapper.HotelRoomTypeMapper;
import com.travel.service.HotelService;
import com.travel.mapper.HotelMapper;
import com.travel.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 酒店服务实现
 * 使用 @ThreeTierCache 注解实现三级缓存 + 三大防护
 */
@Slf4j
@Service
public class HotelServiceImpl extends ServiceImpl<HotelMapper, Hotel>
    implements HotelService{

    @Autowired
    private HotelMapper hotelMapper;

    @Autowired
    private HotelRoomTypeMapper hotelRoomTypeMapper;

    @Autowired
    private HotelRoomMapper hotelRoomMapper;

    @Autowired
    private com.travel.mapper.HotelBookingMapper hotelBookingMapper;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 根据城市获取该城市的酒店信息（支持按名称关键字搜索 + 星级/价格/设施筛选）
     * 三级缓存 + 三大防护全部由AOP处理
     * <p>
     * 注意：缓存 key 只用 city，keyword 与筛选在内存中过滤。
     * 原因是 ThreeTierCacheAspect 的 SpEL 解析不支持含空格的复杂表达式
     * （如 #city + ':' + (#keyword == null ? '' : #keyword)），会导致布隆过滤器误拦截。
     *
     * @param city        城市
     * @param keyword     关键字（可选）
     * @param minStar     最低星级 1-5（可选）
     * @param minPrice    最低价格（可选）
     * @param maxPrice    最高价格（可选）
     * @param facilities  必须包含的设施列表（可选，AND 关系：酒店必须同时包含所有设施）
     */
    @Override
    @ThreeTierCache(
        cacheName = "hotels",
        key = "#city",
        localTtlMinutes = 5,
        redisTtlMinutes = 30,
        redisTtlOffsetMinutes = 5,
        useBloomFilter = true
    )
    public List<HotelVO> getAllHotelInfo(String city, String keyword, Integer minStar, BigDecimal minPrice, BigDecimal maxPrice, List<String> facilities) {
        // 缓存的是该城市所有酒店，keyword 与筛选在内存中过滤
        LambdaQueryWrapper<Hotel> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Hotel::getCity, city);

        List<Hotel> hotelList = hotelMapper.selectList(lambdaQueryWrapper);

        // 批量查所有酒店的最低房型价格（避免 N+1 查询）
        Map<Long, BigDecimal> minPriceMap = batchQueryMinPrices(
            hotelList.stream().map(Hotel::getHotelId).toList()
        );

        List<HotelVO> allHotels = hotelList.stream().map(s -> {
            HotelVO vo = new HotelVO();
            vo.setHotelId(s.getHotelId());
            vo.setAddress(s.getAddress());
            vo.setCity(s.getCity());
            vo.setDescription(s.getDescription());
            vo.setCreateTime(s.getCreateTime());
            vo.setFacilities(parseFacilities(s.getFacilities()));
            vo.setLatitude(s.getLatitude());
            vo.setName(s.getName());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setStar(s.getStar());
            vo.setLongitude(s.getLongitude());
            vo.setContactPhone(s.getContactPhone());
            vo.setMainImage(s.getMainImage());
            vo.setMinPrice(minPriceMap.get(s.getHotelId()));  // 可能为 null（无房型）
            return vo;
        }).toList();

        // keyword 过滤
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            allHotels = allHotels.stream()
                .filter(h -> h.getName() != null && h.getName().contains(kw))
                .toList();
        }

        // 星级过滤
        if (minStar != null && minStar >= 1 && minStar <= 5) {
            allHotels = allHotels.stream()
                .filter(h -> h.getStar() != null && h.getStar() >= minStar)
                .toList();
        }

        // 价格过滤（最低价）
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) > 0) {
            allHotels = allHotels.stream()
                .filter(h -> h.getMinPrice() != null && h.getMinPrice().compareTo(minPrice) >= 0)
                .toList();
        }

        // 价格过滤（最高价）
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) > 0) {
            allHotels = allHotels.stream()
                .filter(h -> h.getMinPrice() != null && h.getMinPrice().compareTo(maxPrice) <= 0)
                .toList();
        }

        // 设施过滤（多选 AND：所有设施都要包含）
        if (facilities != null && !facilities.isEmpty()) {
            List<String> finalFacilities = facilities.stream()
                .filter(f -> f != null && !f.isBlank())
                .map(String::trim)
                .toList();
            if (!finalFacilities.isEmpty()) {
                allHotels = allHotels.stream()
                    .filter(h -> h.getFacilities() != null && containsAllFacilities(h.getFacilities(), finalFacilities))
                    .toList();
            }
        }

        return allHotels;
    }

    /**
     * 批量查多个酒店的最低房型价格，返回 hotelId → minPrice 的映射
     */
    private Map<Long, BigDecimal> batchQueryMinPrices(List<Long> hotelIds) {
        if (hotelIds == null || hotelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 查这些酒店的所有房型价格
        LambdaQueryWrapper<HotelRoomType> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(HotelRoomType::getHotelId, hotelIds);
        wrapper.select(HotelRoomType::getHotelId, HotelRoomType::getPrice);
        List<HotelRoomType> roomTypes = hotelRoomTypeMapper.selectList(wrapper);

        // 按 hotelId 分组取最小值
        Map<Long, BigDecimal> result = new HashMap<>();
        for (HotelRoomType rt : roomTypes) {
            if (rt.getPrice() == null) continue;
            BigDecimal current = result.get(rt.getHotelId());
            if (current == null || rt.getPrice().compareTo(current) < 0) {
                result.put(rt.getHotelId(), rt.getPrice());
            }
        }
        return result;
    }

    /**
     * 判断设施对象中是否同时包含所有指定设施（AND 关系）
     */
    private boolean containsAllFacilities(Map<String, Object> facilities, List<String> required) {
        for (String facility : required) {
            Object value = facilities.get(facility);
            if (value == null) return false;
            if (value instanceof Boolean) {
                if (!(Boolean) value) return false;
            } else if (value instanceof List) {
                if (((List<?>) value).isEmpty()) return false;
            }
            // 其他类型视为存在
        }
        return true;
    }

    /**
     * 根据酒店ID获取酒店详细信息
     * 三级缓存 + 三大防护全部由AOP处理
     */
    @Override
    @ThreeTierCache(
        cacheName = "hotel",
        key = "#hotelId",
        localTtlMinutes = 5,
        redisTtlMinutes = 30,
        redisTtlOffsetMinutes = 5,
        useBloomFilter = true
    )
    public HotelVO getHotelById(Long hotelId) {
        Hotel hotel = hotelMapper.selectById(hotelId);
        if (hotel == null) {
            return null;
        }
        HotelVO vo = new HotelVO();
        vo.setHotelId(hotel.getHotelId());
        vo.setAddress(hotel.getAddress());
        vo.setCity(hotel.getCity());
        vo.setDescription(hotel.getDescription());
        vo.setCreateTime(hotel.getCreateTime());
        vo.setFacilities(parseFacilities(hotel.getFacilities()));
        vo.setLatitude(hotel.getLatitude());
        vo.setName(hotel.getName());
        vo.setUpdateTime(hotel.getUpdateTime());
        vo.setStar(hotel.getStar());
        vo.setLongitude(hotel.getLongitude());
        vo.setContactPhone(hotel.getContactPhone());
        vo.setMainImage(hotel.getMainImage());
        return vo;
    }

    /**
     * 根据酒店ID获取酒店的房间类型信息
     */
    @Override
    @ThreeTierCache(
        cacheName = "roomTypes",
        key = "#hotelId",
        localTtlMinutes = 5,
        redisTtlMinutes = 30,
        redisTtlOffsetMinutes = 5,
        useBloomFilter = true
    )
    public List<HotelRoomTypeVO> getHotelRoomType(Long hotelId) {
        LambdaQueryWrapper<HotelRoomType> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(HotelRoomType::getHotelId, hotelId);

        List<HotelRoomType> result = hotelRoomTypeMapper.selectList(lambdaQueryWrapper);

        return result.stream().map(s -> {
            HotelRoomTypeVO vo = new HotelRoomTypeVO();
            vo.setRoomTypeId(s.getRoomTypeId());
            vo.setHotelId(s.getHotelId());
            vo.setAmenities(parseFacilities(s.getAmenities()));
            vo.setHotelName(hotelMapper.selectById(hotelId).getName());
            vo.setArea(s.getArea());
            vo.setBedType(s.getBedType());
            vo.setCapacity(s.getCapacity());
            vo.setPrice(s.getPrice());
            vo.setName(s.getName());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            return vo;
        }).toList();
    }

    /**
     * 根据酒店ID、房间类型ID、房间号查询房间信息
     * 房间信息不适合缓存（实时性要求高），不添加缓存注解
     */
    @Override
    public List<HotelRoomVO> getHotelRoom(Long hotelId, Long roomTypeId, String roomNo) {
        LambdaQueryWrapper<HotelRoom> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(HotelRoom::getHotelId, hotelId);
        lambdaQueryWrapper.eq(HotelRoom::getRoomTypeId, roomTypeId);
        // roomNo 可选：传了就精确匹配，不传就查该房型所有房间
        if (roomNo != null && !roomNo.isBlank()) {
            lambdaQueryWrapper.eq(HotelRoom::getRoomNo, roomNo.trim());
        }

        List<HotelRoom> result = hotelRoomMapper.selectList(lambdaQueryWrapper);

        List<HotelRoomVO> voList = result.stream().map(s -> {
            HotelRoomVO vo = new HotelRoomVO();
            vo.setRoomId(s.getRoomId());
            vo.setHotelId(s.getHotelId());
            vo.setRoomTypeId(s.getRoomTypeId());
            vo.setRoomNo(s.getRoomNo());
            vo.setFloor(s.getFloor());
            vo.setStatusName(s.getStatus() == 1 ? "可用" : "不可用");
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setHotelName(hotelMapper.selectById(hotelId).getName());
            vo.setRoomTypeName(hotelRoomTypeMapper.selectById(roomTypeId).getName());
            return vo;
        }).toList();
        return voList;
    }

    /**
     * 根据城市、日期、天数来查询当地的酒店是否有空房间
     * 实时性要求高，不适合缓存
     */
    @Override
    public List<HotelEmptyRoomVO> selectEmptyRoom(String city, String startDate, Long days) {
        DateTimeFormatter formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime newStartDate = LocalDateTime.parse(startDate, formatter);
        LocalDateTime endDate = newStartDate.plusDays(days);

        return hotelBookingMapper.selectEmptyRoom(city, newStartDate, endDate);
    }

    /**
     * 把数据库读出来的 facilities 统一转换为 Map<String, Object>
     * 数据库字段是 MySQL JSON 类型，MyBatis 可能以 String/Map/byte[] 形式返回
     * 这里做防御性转换，确保对外接口契约稳定
     */
    private Map<String, Object> parseFacilities(Object raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        // 已经是 Map（MyBatis 把它反序列化为 LinkedHashMap）
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        // 是字符串，尝试 JSON 解析
        if (raw instanceof String) {
            String str = (String) raw;
            if (str.isEmpty()) {
                return Collections.emptyMap();
            }
            try {
                Object parsed = JSON.parse(str);
                if (parsed instanceof JSONObject) {
                    return (Map<String, Object>) parsed;
                }
                // 解析后是数组或其他类型，转空 Map
                log.warn("【HotelServiceImpl】facilities JSON 解析结果不是对象: {}", parsed);
                return Collections.emptyMap();
            } catch (Exception e) {
                log.warn("【HotelServiceImpl】facilities JSON 解析失败: {}", str, e);
                return Collections.emptyMap();
            }
        }
        // 其他类型（byte[] 等），记录警告
        log.warn("【HotelServiceImpl】facilities 类型异常: {}", raw.getClass());
        return Collections.emptyMap();
    }

    /**
     * 把 DTO 转换为 Hotel 实体
     */
    private Hotel dtoToHotel(HotelAdminDTO dto) {
        Hotel hotel = new Hotel();
        if (dto.getHotelId() != null) {
            hotel.setHotelId(dto.getHotelId());
        }
        hotel.setName(dto.getName());
        hotel.setCity(dto.getCity());
        hotel.setAddress(dto.getAddress());
        hotel.setStar(dto.getStar());
        hotel.setLatitude(dto.getLatitude());
        hotel.setLongitude(dto.getLongitude());
        hotel.setContactPhone(dto.getContactPhone());
        // facilities 存为 JSON 字符串，方便 MySQL JSON 列存储
        hotel.setFacilities(dto.getFacilities() == null ? new HashMap<>() : dto.getFacilities());
        hotel.setMainImage(dto.getMainImage());
        hotel.setDescription(dto.getDescription());
        return hotel;
    }

    /**
     * 把 Hotel 实体转换为 HotelVO
     */
    private HotelVO hotelToVO(Hotel hotel) {
        HotelVO vo = new HotelVO();
        vo.setHotelId(hotel.getHotelId());
        vo.setName(hotel.getName());
        vo.setCity(hotel.getCity());
        vo.setAddress(hotel.getAddress());
        vo.setStar(hotel.getStar());
        vo.setLatitude(hotel.getLatitude());
        vo.setLongitude(hotel.getLongitude());
        vo.setContactPhone(hotel.getContactPhone());
        vo.setFacilities(parseFacilities(hotel.getFacilities()));
        vo.setMainImage(hotel.getMainImage());
        vo.setDescription(hotel.getDescription());
        vo.setCreateTime(hotel.getCreateTime());
        vo.setUpdateTime(hotel.getUpdateTime());
        return vo;
    }

    /**
     * 写操作后清除酒店列表缓存（让下次查询走 DB）
     */
    private void clearHotelsCache() {
        try {
            Cache cache = cacheManager.getCache("hotels");
            if (cache != null) {
                cache.clear();
                log.info("【HotelServiceImpl】写操作后已清除 hotels 缓存");
            }
        } catch (Exception e) {
            log.warn("【HotelServiceImpl】清除 hotels 缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public HotelVO createHotel(HotelAdminDTO dto) {
        Hotel hotel = dtoToHotel(dto);
        LocalDateTime now = LocalDateTime.now();
        hotel.setCreateTime(now);
        hotel.setUpdateTime(now);
        hotelMapper.insert(hotel);
        log.info("【HotelServiceImpl】新增酒店 hotelId={}, name={}", hotel.getHotelId(), hotel.getName());
        clearHotelsCache();
        return hotelToVO(hotel);
    }

    @Override
    public HotelVO updateHotel(HotelAdminDTO dto) {
        if (dto.getHotelId() == null) {
            throw new IllegalArgumentException("修改酒店时 hotelId 不能为空");
        }
        Hotel existing = hotelMapper.selectById(dto.getHotelId());
        if (existing == null) {
            throw new IllegalArgumentException("酒店不存在 hotelId=" + dto.getHotelId());
        }
        Hotel hotel = dtoToHotel(dto);
        hotel.setCreateTime(existing.getCreateTime());
        hotel.setUpdateTime(LocalDateTime.now());
        hotelMapper.updateById(hotel);
        log.info("【HotelServiceImpl】更新酒店 hotelId={}", hotel.getHotelId());
        clearHotelsCache();
        return hotelToVO(hotel);
    }

    @Override
    public boolean deleteHotel(Long hotelId) {
        int rows = hotelMapper.deleteById(hotelId);
        log.info("【HotelServiceImpl】删除酒店 hotelId={}, 影响行数={}", hotelId, rows);
        clearHotelsCache();
        return rows > 0;
    }
}
