package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对话消息表
 * @TableName chat_message
 */
@TableName(value ="chat_message")
@Data
public class ChatMessage implements Serializable {
    /**
     * 消息ID
     */
    @TableId(value = "msg_id", type = IdType.AUTO)
    private Long msg_id;

    /**
     * 会话ID
     */
    @TableField(value = "session_id")
    private Long session_id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long user_id;

    /**
     * 角色
     */
    @TableField(value = "role")
    private Object role;

    /**
     * 内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 同步行程
     */
    @TableField(value = "plan_json")
    private Object plan_json;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime create_time;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}