package com.travel.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "会话传输对象")
public class ChatSessionDTO {

//    /**
//     * 用户ID
//     */
//    @NotNull(message = "用户ID不能为空")
//    @Schema(description = "会话ID",requiredMode = Schema.RequiredMode.REQUIRED,example = "3001")
//    private Long user_id;

    /**
     * 会话标题
     */
    @NotNull(message = "标题不能为空")
    @Schema(description = "标题",requiredMode = Schema.RequiredMode.REQUIRED,example = "新会话")
    private String title;
}
