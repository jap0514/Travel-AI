package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "接收python返回的AI回答的传输对象")
public class AiMessageCallbackDTO {
    @Schema(description = "会话ID", example = "3001")
    private Long sessionId;

    @Schema(description = "用户ID", example = "123")
    private Long userId;

    @Schema(description = "关联的用户消息ID（哪条消息触发的AI回复）", example = "2001")
    private Long relatedMsgId;

    @Schema(description = "AI回复内容", example = "好的，我来为您规划...")
    private String content;

    @Schema(description = "行程规划JSON（可选）")
    private String planJson;

    @Schema(description = "全局 trace ID")
    private String traceId;

    @Schema(description = "flow ID（用于中断恢复）")
    private String flowId;

    @Schema(description = "交互状态（等待用户选择时返回）")
    private Object interaction;

    @Schema(description = "任务信息（可选）")
    private TaskInfo task;

    @Data
    @Schema(description = "任务信息")
    public static class TaskInfo {
        @Schema(description = "任务ID", example = "1001")
        private Long taskId;

        @Schema(description = "用户ID", example = "1")
        private Long userId;

        @Schema(description = "用户查询", example = "去北京玩3天")
        private String userQuery;

        @Schema(description = "天数", example = "3")
        private Integer days;

        @Schema(description = "预算", example = "中等")
        private String budget;

        @Schema(description = "节奏", example = "正常")
        private String pace;

        @Schema(description = "目的地", example = "北京")
        private String destination;

        @Schema(description = "计划ID", example = "2001")
        private Long planId;

        @Schema(description = "错误信息")
        private String errorMsg;

        @Schema(description = "结果状态")
        private String resultStatus;
    }
}
