package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "查询订单列表条件")
public class QueryBookingDTO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "订单状态：0待支付 1已支付 2已确认 3已取消 4已完成（可选）")
    private Integer status;

    @Schema(description = "页码（默认1）")
    private Integer page = 1;

    @Schema(description = "每页数量（默认10）")
    private Integer size = 10;
}
