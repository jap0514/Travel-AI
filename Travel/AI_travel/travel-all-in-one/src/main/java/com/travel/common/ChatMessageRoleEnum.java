package com.travel.common;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ChatMessageRoleEnum {
    USER("USER", "用户"),
    ASSISTANT("ASSISTANT", "助手");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    ChatMessageRoleEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() { return code; }
    public String getDesc() { return desc; }
}