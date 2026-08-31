package com.travel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 酒店管理接口请求参数（新增/修改）
 */
@Data
@Schema(description = "酒店管理请求参数")
public class HotelAdminDTO {

    @Schema(description = "酒店ID（修改时必填，新增时不填）")
    private Long hotelId;

    @NotBlank(message = "酒店名称不能为空")
    @Schema(description = "酒店名称")
    private String name;

    @NotBlank(message = "城市不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "城市名格式不正确（2-10个中文）")
    @Schema(description = "城市")
    private String city;

    @Schema(description = "详细地址")
    private String address;

    @NotNull(message = "星级不能为空")
    @Schema(description = "星级（1-5）")
    private Integer star;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "设施列表，例如 [\"WiFi\", \"游泳池\", \"停车场\"]")
    private List<String> facilities;

    @Schema(description = "主图URL")
    private String mainImage;

    @Schema(description = "酒店描述")
    private String description;
}