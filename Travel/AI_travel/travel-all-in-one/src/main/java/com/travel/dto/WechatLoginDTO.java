package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信登录请求参数")
public class WechatLoginDTO {

    @NotBlank(message = "微信code不能为空")
    @Schema(description = "微信小程序登录code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
