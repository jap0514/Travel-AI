package com.travel.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "酒店房间预订传输对象")
public class HotelBookingDTO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "酒店ID")
    private Long hotelId;

    @Schema(description = "房间类型ID")
    private Long roomTypeId;

    @Schema(description = "房间号")
    private String roomNo;

    @Schema(description = "入住日期")
    private LocalDateTime checkInDate;

    @Schema(description = "退房日期")
    private LocalDateTime checkOutDate;

    @Schema(description = "入住人姓名")
    private String guestName;

    @Schema(description = "入住人电话")
    private String guestPhone;

    @Schema(description = "特殊要求（可选）")
    private String specialRequest;
}
