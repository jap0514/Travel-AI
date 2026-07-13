package com.travel.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "planjson拆分后的单个活动对象")
public class Activity implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("name")
    @Schema(description = "活动名称", example = "参观上海博物馆")
    private String name;

    @JsonProperty("time")
    @Schema(description = "时间段", example = "9:00-12:00")
    private String time;

    @JsonProperty("description")
    @Schema(description = "活动具体描述")
    private String description;

    @JsonProperty("location")
    @Schema(description = "具体地点", example = "黄浦区人民大道201号")
    private String location;

    @JsonProperty("transportation")
    @Schema(description = "前往该地的交通方式", example = "地铁1号线")
    private String transportation;

    @JsonProperty("cost")
    @Schema(description = "该活动的预计花费", example = "100")
    private BigDecimal cost;
}
