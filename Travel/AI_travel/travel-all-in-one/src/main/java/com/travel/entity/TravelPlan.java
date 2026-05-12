package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 行程规划表
 * @TableName travel_plan
 */
@TableName(value ="travel_plan")
@Data
public class TravelPlan implements Serializable {
    /**
     * 行程单ID
     */
    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long plan_id;

    /**
     * 任务ID
     */
    @TableField(value = "task_id")
    private Long task_id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long user_id;

    /**
     * 标题
     */
    @TableField(value = "title")
    private String title;

    /**
     * 目的地
     */
    @TableField(value = "destination")
    private String destination;

    /**
     * 天数
     */
    @TableField(value = "days")
    private Integer days;

    /**
     * 行程JSON
     */
    @TableField(value = "content")
    private Object content;

    /**
     * 天气
     */
    @TableField(value = "weather")
    private String weather;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime create_time;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}