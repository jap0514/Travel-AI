package com.travel.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "酒店房间预订传输对象")
public class HotelBookingDTO {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @NotNull(message = "酒店ID不能为空")
    @Schema(description = "酒店ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long hotelId;

    @NotNull(message = "房间类型ID不能为空")
    @Schema(description = "房间类型ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roomTypeId;

    @NotBlank(message = "房间号不能为空")
    @Schema(description = "房间号", requiredMode = Schema.RequiredMode.REQUIRED, example = "801")
    private String roomNo;

    @NotNull(message = "入住日期不能为空")
    @Schema(description = "入住日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime checkInDate;

    @NotNull(message = "退房日期不能为空")
    @Schema(description = "退房日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime checkOutDate;

    @NotBlank(message = "入住人姓名不能为空")
    @Schema(description = "入住人姓名", requiredMode = Schema.RequiredMode.REQUIRED, example = "张三")
    private String guestName;

    @NotBlank(message = "入住人电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "入住人电话", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    private String guestPhone;

    @Schema(description = "特殊要求（可选）")
    private String specialRequest;
}
