package com.travel.es.converter;

import com.travel.entity.Hotel;
import com.travel.es.doc.HotelDoc;
import com.travel.util.FacilityNormalizer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hotel 实体 → HotelDoc 转换器
 * <p>
 * 主要工作：
 * <ol>
 *   <li>字段映射：hotel.facilities（JSON 字符串数组）→ List&lt;String&gt;</li>
 *   <li>字段映射：hotel.latitude + longitude → GeoPoint</li>
 *   <li>价格填入：minPrice 由调用方提供（来自 hotel_room_type 表子查询）</li>
 *   <li>防御性处理：null / 空值的兜底</li>
 * </ol>
 *
 * @author travel
 */
public final class HotelEsConverter {

    private HotelEsConverter() {
        // 工具类不允许实例化
    }

    /**
     * Hotel 实体 → HotelDoc 转换
     *
     * @param hotel    MySQL hotel 实体（hotelId 必须非空）
     * @param minPrice 来自 hotel_room_type 表的 MIN(price)，可为 null（ES 字段允许空）
     * @return HotelDoc 文档对象
     */
    public static HotelDoc toDoc(Hotel hotel, BigDecimal minPrice) {
        if (hotel == null) {
            throw new IllegalArgumentException("hotel 不能为 null");
        }

        HotelDoc doc = new HotelDoc();

        // 1. 直接映射字段
        doc.setHotelId(hotel.getHotelId());
        doc.setName(hotel.getName());
        doc.setCity(hotel.getCity());
        doc.setAddress(hotel.getAddress());
        doc.setStar(hotel.getStar());
        doc.setContactPhone(hotel.getContactPhone());
        doc.setMainImage(hotel.getMainImage());
        doc.setDescription(hotel.getDescription());
        doc.setCreateTime(hotel.getCreateTime());
        doc.setUpdateTime(hotel.getUpdateTime());

        // 2. 归一化 facilities（JSON 字符串数组 → List<String>）
        doc.setFacilities(FacilityNormalizer.normalize(hotel.getFacilities()));

        // 3. 组装 GeoPoint（经纬度任一为空则不写入 location 字段）
        doc.setLocation(buildGeoPoint(hotel.getLatitude(), hotel.getLongitude()));

        // 4. 价格：BigDecimal → Double（ES mapping 是 double 类型）
        doc.setMinPrice(toDouble(minPrice));

        return doc;
    }

    /**
     * 组装地理坐标（ES 8.x geo_point 接受 {"lat":X, "lon":Y} 格式）
     * <p>
     * 用 LinkedHashMap 保证序列化顺序（lat 在前，lon 在后），
     * ES geo_point 兼容两种顺序，但保持稳定更易调试。
     * <p>
     * 经纬度任一为 null 时返回 null（不写入 ES 的 location 字段），
     * 避免 ES 写入时因 location=null 报错。
     *
     * @param lat 纬度
     * @param lng 经度
     * @return Map（key 为 "lat"/"lon"）或 null
     */
    private static Map<String, Double> buildGeoPoint(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return null;
        }
        Map<String, Double> map = new LinkedHashMap<>(2);
        map.put("lat", lat.doubleValue());
        map.put("lon", lng.doubleValue());
        return map;
    }

    /**
     * BigDecimal → Double 安全转换
     */
    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
