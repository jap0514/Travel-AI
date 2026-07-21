package com.travel.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "酒店空房间信息视图")
public class HotelEmptyRoomVO {
    private Long hotelId;
    private Long roomTypeId;
    private String roomNo;
    private String hotelName;
    private String roomTypeName;
    @Schema(description = "判断是否有空房间")
    private Boolean hasEmptyRoom;
    @Schema(description = "酒店地址")
    private String address;
    @Schema(description = "房间价格")
    private Double price;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    @Schema(description = "如果有空房间就直接返回当前时间，如果没有空房间，就查询订单表里面的退房时间返回")
    private LocalDateTime availableBookTime;
}
