package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 酒店搜索请求参数
 * <p>
 * 由 Controller 接收 query 参数后组装，传给 HotelEsSearchService。
 * 字段尽量简单，便于序列化与扩展。
 *
 * @author travel
 */
@Data
@Schema(description = "酒店搜索请求参数")
public class HotelQuery {

    /** 城市（必填） */
    @Schema(description = "城市名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String city;

    /** 关键字：酒店名 / 地址 / 描述（可选） */
    @Schema(description = "关键字，支持中文 + 拼音（如 lujiazui）")
    private String keyword;

    /** 最低星级（1-5，可选） */
    @Schema(description = "最低星级 1-5")
    private Integer minStar;

    /** 最低价格（可选） */
    @Schema(description = "最低价格（元/晚）")
    private BigDecimal minPrice;

    /** 最高价格（可选） */
    @Schema(description = "最高价格（元/晚）")
    private BigDecimal maxPrice;

    /** 必须包含的设施列表（AND 关系） */
    @Schema(description = "必须包含的设施列表")
    private List<String> facilities;

    /** 当前页码（从 1 开始，默认 1） */
    @Schema(description = "页码，从 1 开始")
    private Integer page = 1;

    /** 每页大小（默认 20） */
    @Schema(description = "每页大小")
    private Integer size = 20;

    /** 排序字段：默认按 _score 排，可选：minPrice_asc / star_desc */
    @Schema(description = "排序方式：default / price_asc / price_desc / star_desc")
    private String sort = "default";
}
