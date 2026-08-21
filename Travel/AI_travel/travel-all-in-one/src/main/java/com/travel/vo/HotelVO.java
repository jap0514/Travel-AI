package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Schema(description = "酒店信息视图")
public class HotelVO {

    private Long hotelId;

    private String name;

    private String city;

    private String address;

    private Integer star;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String contactPhone;

    @Schema(description = "设施键值对，例如 { \"WiFi\": true, \"餐厅\": [\"中餐\", \"西餐\"] }")
    private Map<String, Object> facilities;

    private String mainImage;

    private String description;

    @Schema(description = "该酒店所有房型中的最低价格（元/晚）")
    private BigDecimal minPrice;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
