package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 旅行行程表
 * @TableName travel_parse_plan
 */
@TableName(value ="travel_parse_plan")
@Data
public class TravelParsePlan implements Serializable {
    /**
     * 行程单ID
     */
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long plan_id;

    /**
     * 关联任务ID
     */
    @TableField(value = "task_id")
    private Long task_id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long user_id;

    /**
     * 行程单标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 目的地城市
     */
    @TableField(value = "destination")
    private String destination;

    /**
     * 旅行天数
     */
    @TableField(value = "days")
    private Integer days;

    /**
     * 预算(低/中/高)
     */
    @TableField(value = "budget")
    private String budget;

    /**
     * 节奏(轻松/正常/紧凑)
     */
    @TableField(value = "pace")
    private String pace;

    /**
     * 出发日期
     */
    @TableField(value = "start_date")
    private LocalDate start_date;

    /**
     * 每日行程明细
     */
    @TableField(value = "daily_plans")
    private Object daily_plans;

    /**
     * 总预估费用
     */
    @TableField(value = "total_estimated_cost")
    private BigDecimal total_estimated_cost;

    /**
     * 备注/注意事项
     */
    @TableField(value = "notes")
    private String notes;

    /**
     * AI生成的原始Markdown
     */
    @TableField(value = "raw_markdown")
    private String raw_markdown;

    /**
     * 全链路追踪ID
     */
    @TableField(value = "trace_id")
    private String trace_id;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime create_time;

    /**
     * 
     */
    @TableField(value = "update_time")
    private LocalDateTime update_time;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}