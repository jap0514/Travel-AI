package com.travel.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MessageDTO {
    private Long sessionId;
    private Long userId;
    private String content;
    private Map<String, Object> planJson; // 行程JSON
}
