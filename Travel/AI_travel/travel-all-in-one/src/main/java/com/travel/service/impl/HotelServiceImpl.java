package com.travel.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
import java.util.stream.Collectors;
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

    @Autowired
    private HotelEsSearchService hotelEsSearchService;

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
    public com.travel.vo.HotelSearchResultVO getAllHotelInfo(String city, String keyword, Integer minStar, BigDecimal minPrice, BigDecimal maxPrice, List<String> facilities) {
        // ========== v2 改造：原方案 MySQL + 内存过滤 → 改为 ES bool query ==========
        // ES 1 万酒店响应 P95 < 30ms，且支持拼音/相关性排序/聚合
        // 去掉 @ThreeTierCache 注解（ES 本身已够快）

        // 1. 组装 ES 查询参数
        com.travel.dto.HotelQuery query = new com.travel.dto.HotelQuery();
        query.setCity(city);
        query.setKeyword(keyword);
        query.setMinStar(minStar);
        query.setMinPrice(minPrice);
        query.setMaxPrice(maxPrice);
        query.setFacilities(facilities);
        query.setPage(1);
        query.setSize(100);

        // 2. 调 ES 搜索
        com.travel.vo.HotelSearchResultVO esResult = hotelEsSearchService.search(query);

        // 3. 补充 lat/lng/contactPhone/createTime（这些 ES 文档里没有，从 MySQL 二次查）
        if (esResult.getHotels() != null && !esResult.getHotels().isEmpty()) {
            List<Long> hotelIds = esResult.getHotels().stream()
                    .map(com.travel.vo.HotelSearchResultVO.HotelDocVO::getHotelId).toList();
            Map<Long, Hotel> hotelMap = hotelMapper.selectBatchIds(hotelIds).stream()
                    .collect(Collectors.toMap(Hotel::getHotelId, h -> h, (a, b) -> a));
            for (com.travel.vo.HotelSearchResultVO.HotelDocVO dv : esResult.getHotels()) {
                Hotel h = hotelMap.get(dv.getHotelId());
                if (h != null) {
                    dv.setLatitude(h.getLatitude());
                    dv.setLongitude(h.getLongitude());
                    dv.setContactPhone(h.getContactPhone());
                    dv.setCreateTime(h.getCreateTime());
                    dv.setUpdateTime(h.getUpdateTime());
                }
            }
        }
        return esResult;
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
     * 判断酒店设施列表中是否同时包含所有指定设施（AND 关系）
     */
    private boolean containsAllFacilities(List<String> facilities, List<String> required) {
        if (facilities == null || facilities.isEmpty()) return false;
        for (String facility : required) {
            if (!facilities.contains(facility)) return false;
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
     * 把数据库读出来的 facilities 统一转换为 List<String>
     * 数据库字段是 MySQL JSON 数组，MyBatis 可能以 String/List 形式返回
     * 这里做防御性转换，确保对外接口契约稳定
     */
    private List<String> parseFacilities(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        // 已经是 List（MyBatis 直接反序列化为 List）
        if (raw instanceof List) {
            return (List<String>) raw;
        }
        // 是字符串，尝试 JSON 解析
        if (raw instanceof String) {
            String str = (String) raw;
            if (str.isEmpty()) {
                return Collections.emptyList();
            }
            try {
                Object parsed = JSON.parse(str);
                if (parsed instanceof JSONArray) {
                    return ((JSONArray) parsed).toJavaList(String.class);
                }
                log.warn("【HotelServiceImpl】facilities JSON 解析结果不是数组: {}", parsed);
                return Collections.emptyList();
            } catch (Exception e) {
                log.warn("【HotelServiceImpl】facilities JSON 解析失败: {}", str, e);
                return Collections.emptyList();
            }
        }
        log.warn("【HotelServiceImpl】facilities 类型异常: {}", raw.getClass());
        return Collections.emptyList();
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
        // facilities 存为 JSON 字符串（Entity 是 String），方便 MySQL JSON 列存储
        hotel.setFacilities(JSON.toJSONString(
                dto.getFacilities() == null ? java.util.Collections.emptyList() : dto.getFacilities()));
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
