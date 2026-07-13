package com.travel.common;

public enum TaskStatusEnum {
    INIT("INIT","解析任务，完成任务初始化"),
    SUCCESS("SUCCESS","任务成功完成"),
    FAIL("FAIL","完成任务途中出现错误");

    private final String code;          // 状态码（与数据库存储值一致）
    private final String description;   // 状态描述

    TaskStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
