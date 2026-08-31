package com.travel.es.doc;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 酒店 ES 文档对象（与 ES 索引 hotel_v1 的 mapping 一一对应）
 * <p>
 * 设计要点：
 * <ul>
 *   <li>字段名严格匹配 hotel_v1.json 的 mapping（snake_case 保持 camelCase）</li>
 *   <li>facilities 用 List<String>，写入 ES 后是 keyword 数组（支持 terms 过滤）</li>
 *   <li>location 用 Map&lt;String, Double&gt;，序列化为 {"lat":X,"lon":Y}（geo_point 接受的对象格式）</li>
 *   <li>时间字段 @JsonFormat 标注 ISO8601 格式（ES 默认期望 ISO8601）</li>
 * </ul>
 * <p>
 * 与 Hotel 实体的差异（额外字段）：
 * <ul>
 *   <li>minPrice —— 来自 hotel_room_type 表的子查询 MIN(price)</li>
 *   <li>facilities —— 从 Hotel.facilities（JSON String）归一化而来</li>
 *   <li>location —— 从 Hotel.latitude / longitude 组装</li>
 * </ul>
 *
 * @author travel
 */
@Data
@NoArgsConstructor
public class HotelDoc {

    /** 酒店主键（对应 ES 文档 _id）*/
    private Long hotelId;

    /** 酒店名（text + ik_pinyin_analyzer，附带 keyword/pinyin/suggest 子字段）*/
    private String name;

    /** 城市（keyword，用于精确过滤与聚合）*/
    private String city;

    /** 地址（text + ik_max_word，用于全文搜索）*/
    private String address;

    /** 星级 1-5（integer，用于范围过滤）*/
    private Integer star;

    /**
     * 地理坐标（ES geo_point 类型）
     * <p>
     * 用 Map&lt;String, Double&gt; 代替 ES Java 客户端的 GeoLocation 类型，
     * 因为后者是 sealed union 类型，Jackson 序列化时会包含类型包装，
     * ES geo_point 解析失败。
     * <p>
     * 序列化为：{"lat": 31.2367, "lon": 121.5055}
     */
    private Map<String, Double> location;

    /** 最低房价（来自 hotel_room_type 子查询，非 hotel 表字段）*/
    private Double minPrice;

    /** 联系电话 */
    private String contactPhone;

    /** 设施列表（keyword 数组，支持 terms AND 过滤）*/
    private List<String> facilities;

    /** 主图 URL（不建索引，仅返回）*/
    private String mainImage;

    /** 描述（text + ik_max_word）*/
    private String description;

    /** 创建时间（date，ISO8601）*/
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间（date，ISO8601）*/
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updateTime;
}
