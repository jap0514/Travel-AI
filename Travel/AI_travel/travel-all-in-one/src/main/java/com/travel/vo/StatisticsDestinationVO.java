package com.travel.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "平台旅游规划目的地视图")
public class StatisticsDestinationVO {
    @Schema(description = "目的地",example = "广州")
    private String destination;

    @Schema(description = "次数",example = "5")
    private Long count;

}
