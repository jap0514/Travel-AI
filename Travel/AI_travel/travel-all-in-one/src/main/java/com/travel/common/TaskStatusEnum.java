package com.travel.common;

public enum TaskStatusEnum {
    INIT("INIT", "获取请求，写入数据库"),
    QUEUED("QUEUED", "消息成功发送到 RocketMQ"),
    PROCESSING("PROCESSING", "Python 开始处理并回传进度消息"),
    COMPLETED("COMPLETED", "Java 成功解析并持久化最终结果"),
    FAILED("FAILED", "任何环节出现不可恢复的错误");

    private final String code;          // 状态码（与数据库存储值一致）
    private final String description;   // 状态描述

    TaskStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
