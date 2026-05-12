package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "微信登录请求参数")
public class WechatLoginDTO {

    @Schema(description = "微信小程序登录code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}