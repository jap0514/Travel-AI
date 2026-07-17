package com.travel.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "酒店房间类型信息视图")
public class HotelRoomTypeVO {
    /**
     * 房间类型ID
     */
    private Long roomTypeId;

    /**
     * 所属酒店ID
     */
    private Long hotelId;

    /**
     * 所属酒店
     */
    private String hotelName;

    /**
     * 房型名称
     */
    private String name;

    /**
     * 单价/晚
     */
    private BigDecimal price;

    /**
     * 可住人数
     */
    private Integer capacity;

    /**
     * 床型
     */
    private String bedType;

    /**
     * 面积
     */
    private String area;

    /**
     * 房间设施
     */
    private Object amenities;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
