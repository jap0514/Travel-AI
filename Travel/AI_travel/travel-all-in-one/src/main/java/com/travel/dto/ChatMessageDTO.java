package com.travel.dto;

import com.travel.common.ChatMessageRoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "对话消息传输对象")
public class ChatMessageDTO {

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "3001")
    private Long sessionId;

//    @NotNull(message = "用户ID不能为空")
//    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
//    private Long userId;

    @NotNull(message = "角色不能为空")
    @Schema(description = "角色", requiredMode = Schema.RequiredMode.REQUIRED, example = "USER")
    private ChatMessageRoleEnum role;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "请帮我规划一个北京3日游")
    private String content;

    @Schema(description = "同步行程JSON（可选）", example = "{\"planId\":1001}")
    private String planJson;
}