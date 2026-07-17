package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "支付订单请求")
public class PayOrderDTO {

    @Schema(description = "交易流水号（模拟支付时可选）")
    private String transactionId;
}
