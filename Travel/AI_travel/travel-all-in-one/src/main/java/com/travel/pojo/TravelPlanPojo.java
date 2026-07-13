package com.travel.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "planjson解析后的完整旅行计划POJO")
public class TravelPlanPojo implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("user_id")
    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @JsonProperty("daily_plans")
    @Schema(description = "每日行程列表")
    private List<DailyActivity> dailyPlans;

    @JsonProperty("title")
    @Schema(description = "计划标题", example = "北京5日深度游")
    private String title;

    @JsonProperty("days")
    @Schema(description = "旅行天数", example = "5")
    private Integer days;

    @JsonProperty("budget")
    @Schema(description = "预算(低/中/高)", example = "中等")
    private String budget;

    @JsonProperty("pace")
    @Schema(description = "节奏(轻松/正常/紧凑)", example = "正常")
    private String pace;

    @JsonProperty("task_id")
    @Schema(description = "任务ID", example = "1001")
    private Integer taskId;

    @JsonProperty("plan_id")
    @Schema(description = "计划ID", example = "2001")
    private Integer planId;

    @JsonProperty("destination")
    @Schema(description = "目的地城市", example = "北京")
    private String destination;

    @JsonProperty("start_date")
    @Schema(description = "出发日期")
    private LocalDate startDate;

    @JsonProperty("total_estimated_cost")
    @Schema(description = "总预估费用", example = "3000")
    private BigDecimal totalEstimatedCost;

    @JsonProperty("notes")
    @Schema(description = "备注")
    private String notes;

    @JsonProperty("raw_markdown")
    @Schema(description = "AI生成的原始Markdown")
    private String rawMarkdown;
}
