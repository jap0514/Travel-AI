package com.travel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 任务提交DTO（前端--->Java）
 */

@Data
public class TaskDTO {
    @NotBlank(message = "用户需求不能为空")
    @Size(max = 500, message = "用户需求字数不能超过500")
    private String userQuery;          // 对应 user_query

    @NotNull(message = "出行天数不能为空")
    @Positive(message = "出行天数必须为正整数")
    private Integer days;              // 对应 days

    @NotBlank(message = "预算不能为空")
    private String budget;             // 对应 budget（可存 "5000-8000" 或数字字符串）

    @NotBlank(message = "节奏不能为空")
    private String pace;               // 对应 pace（如 休闲、紧凑）
}
