package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.travel.common.ChatMessageRoleEnum;
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
    private Long msgId;

    /**
     * 会话ID
     */
    @TableField(value = "session_id")
    private Long sessionId;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 角色
     */
    @TableField(value = "role")
    private ChatMessageRoleEnum role;

    /**
     * 内容
     */
    @TableField(value = "content")
    private String content;

    /**
     * 同步行程
     */
    @TableField(value = "plan_json")
    private String planJson;

    /**
     * flow ID（用于中断恢复）
     */
    @TableField(value = "flow_id")
    private String flowId;

    /**
     * 交互状态（等待用户选择时返回）
     */
    @TableField(value = "interaction")
    private String interaction;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}