package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 酒店信息
 * @TableName hotel
 */
@TableName(value ="hotel")
@Data
public class Hotel implements Serializable {
    /**
     * 
     */
    @TableId(value = "hotel_id", type = IdType.AUTO)
    private Long hotelId;

    /**
     * 酒店名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 城市
     */
    @TableField(value = "city")
    private String city;

    /**
     * 地址
     */
    @TableField(value = "address")
    private String address;

    /**
     * 星级
     */
    @TableField(value = "star")
    private Integer star;

    /**
     * 纬度
     */
    @TableField(value = "latitude")
    private BigDecimal latitude;

    /**
     * 经度
     */
    @TableField(value = "longitude")
    private BigDecimal longitude;

    /**
     * 电话
     */
    @TableField(value = "contact_phone")
    private String contactPhone;

    /**
     * 设施
     */
    @TableField(value = "facilities")
    private Object facilities;

    /**
     * 主图
     */
    @TableField(value = "main_image")
    private String mainImage;

    /**
     * 描述
     */
    @TableField(value = "description")
    private String description;

    /**
     * 
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}