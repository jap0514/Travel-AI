package com.travel.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "planjson拆分后的每日行程对象")
public class DailyActivity implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("day")
    @Schema(description = "第几天", example = "1")
    private Integer day;

    @JsonProperty("theme")
    @Schema(description = "当日主题", example = "故宫与天安门")
    private String theme;

    @JsonProperty("activities")
    @Schema(description = "活动列表")
    private List<Activity> activities;

    @JsonProperty("location")
    @Schema(description = "当日主要活动区域", example = "北京市东城区")
    private String location;

    @JsonProperty("transportation")
    @Schema(description = "当日主要交通方式", example = "地铁")
    private String transportation;

    @JsonProperty("meals")
    @Schema(description = "用餐安排", example = "[\"早餐\",\"午餐\",\"晚餐\"]")
    private List<String> meals;

    @JsonProperty("tips")
    @Schema(description = "当日提示", example = "提前预约门票")
    private String tips;

    @JsonProperty("estimated_cost")
    @Schema(description = "当日预估费用", example = "200")
    private BigDecimal estimatedCost;
}
