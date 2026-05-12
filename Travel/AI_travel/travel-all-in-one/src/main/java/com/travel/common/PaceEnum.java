package com.travel.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaceEnum {
    LEISURE("LEISURE", "悠闲"),
    NORMAL("NORMAL", "常规"),
    INTENSE("INTENSE", "紧凑");

    @EnumValue
    @JsonValue   //不加这个的话，前端接口会返回整个枚举对象，加了之后，前端接口只返回 code值
    private final String code;
    private final String desc;

    PaceEnum(String code, String desc) {
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