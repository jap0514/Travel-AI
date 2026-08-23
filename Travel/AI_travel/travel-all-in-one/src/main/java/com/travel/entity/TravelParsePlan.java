package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 旅行行程表
 * <p>
 * 注意：字段名必须用驼峰，依赖 application.yml 的 map-underscore-to-camel-case: true
 * 自动转成数据库列名 plan_id、task_id 等。
 * <p>
 * Jackson 序列化也用驼峰命名（planId 而不是 plan_id），与前端 Vue/JS 一致。
 *
 * @TableName travel_parse_plan
 */
@TableName(value = "travel_parse_plan")
@Data
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class TravelParsePlan implements Serializable {

    /**
     * 行程单ID
     */
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;

    /**
     * 关联任务ID
     */
    @TableField(value = "task_id")
    private Long taskId;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 行程单标题
     */
    private String title;

    /**
     * 目的地城市
     */
    private String destination;

    /**
     * 旅行天数
     */
    private Integer days;

    /**
     * 预算(低/中/高)
     */
    private String budget;

    /**
     * 节奏(轻松/正常/紧凑)
     */
    private String pace;

    /**
     * 出发日期
     */
    @TableField(value = "start_date")
    private LocalDate startDate;

    /**
     * 每日行程明细
     */
    @TableField(value = "daily_plans")
    private Object dailyPlans;

    /**
     * 总预估费用
     */
    @TableField(value = "total_estimated_cost")
    private BigDecimal totalEstimatedCost;

    /**
     * 备注/注意事项
     */
    private String notes;

    /**
     * AI生成的原始Markdown
     */
    @TableField(value = "raw_markdown")
    private String rawMarkdown;

    /**
     * 全链路追踪ID
     */
    @TableField(value = "trace_id")
    private String traceId;

    /**
     *
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     *
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}