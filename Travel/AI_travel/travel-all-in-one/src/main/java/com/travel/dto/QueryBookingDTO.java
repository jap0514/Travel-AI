package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "查询订单列表条件")
public class QueryBookingDTO {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @Schema(description = "订单状态：0待支付 1已支付 2已确认 3已取消 4已完成（可选）")
    private Integer status;

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码（默认1）")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量（默认10）")
    private Integer size = 10;
}
