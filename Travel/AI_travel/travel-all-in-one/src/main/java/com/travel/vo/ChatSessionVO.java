package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "会话视图")
public class ChatSessionVO {
    @Schema(description = "会话ID",example = "3001")
    private Long sessionId;

    @Schema(description = "会话标题",example = "新会话")
    private String title;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
