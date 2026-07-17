package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "取消订单请求")
public class CancelOrderDTO {

    @Schema(description = "取消原因")
    private String cancelReason;
}
