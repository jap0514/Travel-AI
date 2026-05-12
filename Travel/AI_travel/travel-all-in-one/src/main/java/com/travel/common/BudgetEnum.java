package com.travel.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BudgetEnum {
    LOW("LOW", "经济型"),
    MEDIUM("MEDIUM", "中等型"),
    HIGH("HIGH", "豪华型");

    @EnumValue   // 标记数据库存的值
    @JsonValue   // 序列化时返回 code
    private final String code;
    private final String desc;

    BudgetEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}