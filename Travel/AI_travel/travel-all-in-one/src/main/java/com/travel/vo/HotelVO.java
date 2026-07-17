package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private Object facilities;

    private String mainImage;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
