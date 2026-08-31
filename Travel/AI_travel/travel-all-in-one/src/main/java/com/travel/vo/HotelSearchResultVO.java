package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 酒店搜索结果（ES 版）
 * <p>
 * 比 HotelVO 多了：
 * <ul>
 *   <li>score —— ES 相关性评分</li>
 *   <li>highlights —— 关键字高亮（map 字段名 → 高亮文本列表）</li>
 * </ul>
 * 同时返回 total + aggregations 给前端做筛选器。
 *
 * @author travel
 */
@Data
@Schema(description = "酒店搜索结果")
public class HotelSearchResultVO {

    /** 酒店列表 */
    @Schema(description = "酒店列表")
    private List<HotelDocVO> hotels;

    /** 总命中数 */
    @Schema(description = "总命中数")
    private long total;

    /** 当前页（从 1 开始） */
    private int page;

    /** 每页大小 */
    private int size;

    /** 总页数（ceil(total/size)） */
    private int totalPages;

    /** 聚合结果：城市 → 数量 */
    @Schema(description = "城市聚合，前端可做城市筛选器")
    private Map<String, Long> cityAgg;

    /** 聚合结果：星级 → 数量 */
    @Schema(description = "星级聚合")
    private Map<Integer, Long> starAgg;

    /**
     * 酒店文档视图（轻量，只包含搜索结果需要的字段）
     */
    @Data
    @Schema(description = "酒店文档视图（搜索结果）")
    public static class HotelDocVO {
        private Long hotelId;
        private String name;
        private String city;
        private String address;
        private Integer star;
        private Double minPrice;
        private List<String> facilities;
        private String mainImage;
        private String description;
        private Double score;
        private Map<String, List<String>> highlights;
        // 下面字段从 MySQL 二次查询补充（ES 文档未存）
        private java.math.BigDecimal latitude;
        private java.math.BigDecimal longitude;
        private String contactPhone;
        private java.time.LocalDateTime createTime;
        private java.time.LocalDateTime updateTime;
    }
}
