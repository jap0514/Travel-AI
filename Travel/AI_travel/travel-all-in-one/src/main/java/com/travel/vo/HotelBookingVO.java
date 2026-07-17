package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单创建成功返回对象")
public class HotelBookingVO {

    @Schema(description = "订单ID")
    private Long bookingId;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "酒店ID")
    private Long hotelId;

    @Schema(description = "酒店名称")
    private String hotelName;

    @Schema(description = "房间类型ID")
    private Long roomTypeId;

    @Schema(description = "房间类型名称")
    private String roomTypeName;

    @Schema(description = "房间号")
    private String roomNo;

    @Schema(description = "入住日期")
    private LocalDateTime checkInDate;

    @Schema(description = "退房日期")
    private LocalDateTime checkOutDate;

    @Schema(description = "入住天数")
    private Long days;

    @Schema(description = "订单总价")
    private BigDecimal totalPrice;

    @Schema(description = "入住人姓名")
    private String guestName;

    @Schema(description = "入住人电话")
    private String guestPhone;

    @Schema(description = "订单状态：0待支付 1已支付 2已确认 3已取消 4已完成")
    private Integer status;

    @Schema(description = "状态描述")
    private String statusName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "是否预订成功")
    private Boolean success;

    @Schema(description = "错误信息（预订失败时返回）")
    private String errorMessage;
}
