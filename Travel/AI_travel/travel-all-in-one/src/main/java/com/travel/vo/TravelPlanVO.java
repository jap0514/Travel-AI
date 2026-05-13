package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "行程规划视图")
public class TravelPlanVO {

    @Schema(description = "行程单ID", example = "10001")
    private Long planId;

    @Schema(description = "任务ID", example = "5001")
    private Long taskId;

    @Schema(description = "用户ID", example = "123")
    private Long userId;

    @Schema(description = "用户昵称（冗余关联）", example = "张三")
    private String userNickname;

    @Schema(description = "标题", example = "北京三日文化之旅")
    private String title;

    @Schema(description = "目的地", example = "北京")
    private String destination;

    @Schema(description = "天数", example = "3")
    private Integer days;

    @Schema(description = "行程JSON内容", example = "{\"day1\":[...]}")
    private String content;

    @Schema(description = "天气信息", example = "晴 18~26℃")
    private String weather;

    @Schema(description = "创建时间", example = "2025-03-15T10:30:00")
    private LocalDateTime createTime;
}