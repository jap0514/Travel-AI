package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 行程任务表
 * @TableName travel_task
 */
@TableName(value ="travel_task")
@Data
public class TravelTask implements Serializable {
    /**
     * 任务ID
     */
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long task_id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long user_id;

    /**
     * 用户需求
     */
    @TableField(value = "user_query")
    private String user_query;

    /**
     * 出行天数
     */
    @TableField(value = "days")
    private Integer days;

    /**
     * 预算
     */
    @TableField(value = "budget")
    private String budget;

    /**
     * 节奏
     */
    @TableField(value = "pace")
    private String pace;

    /**
     * 
     */
    @TableField(value = "status")
    private Object status;

    /**
     * 行程单ID
     */
    @TableField(value = "plan_id")
    private Long plan_id;

    /**
     * 失败原因
     */
    @TableField(value = "error_msg")
    private String error_msg;

    /**
     * 全链路ID
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