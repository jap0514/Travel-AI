package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "行程规划数据传输对象")
public class TravelPlanDTO {

    @Schema(description = "任务ID", example = "5001")
    private Long taskId;

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    private Long userId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题长度不超过128")
    @Schema(description = "标题", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128, example = "北京三日文化之旅")
    private String title;

    @NotBlank(message = "目的地不能为空")
    @Size(max = 64, message = "目的地长度不超过64")
    @Schema(description = "目的地", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64, example = "北京")
    private String destination;

    @NotNull(message = "天数不能为空")
    @Schema(description = "天数", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer days;

    @NotBlank(message = "行程内容不能为空")
    @Schema(description = "行程JSON内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "{\"day1\":[...]}")
    private String content;

    @Schema(description = "天气信息", example = "晴 18~26℃")
    private String weather;
}