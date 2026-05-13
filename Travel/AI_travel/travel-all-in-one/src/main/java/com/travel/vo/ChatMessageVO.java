package com.travel.vo;

import com.travel.common.ChatMessageRoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "对话消息视图")
public class ChatMessageVO {

    @Schema(description = "消息ID", example = "2001")
    private Long msgId;

    @Schema(description = "会话ID", example = "3001")
    private Long sessionId;

    @Schema(description = "用户ID", example = "123")
    private Long userId;

    @Schema(description = "发送者角色", example = "USER")
    private ChatMessageRoleEnum role;

    @Schema(description = "消息内容", example = "请帮我规划一个北京3日游")
    private String content;

    @Schema(description = "同步的行程JSON（仅ASSISTANT消息可能携带）", example = "{\"planId\":1001}")
    private String planJson;

    @Schema(description = "发送时间", example = "2025-03-15T10:30:00")
    private LocalDateTime createTime;

    @Schema(description = "用户昵称", example = "张三")
    private String userNickname;
}