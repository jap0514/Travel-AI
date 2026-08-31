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
 * 房型表
 * @TableName hotel_room_type
 */
@TableName(value ="hotel_room_type")
@Data
public class HotelRoomType implements Serializable {
    /**
     * 
     */
    @TableId(value = "room_type_id", type = IdType.AUTO)
    private Long roomTypeId;

    /**
     * 所属酒店
     */
    @TableField(value = "hotel_id")
    private Long hotelId;

    /**
     * 房型名称
     */
    @TableField(value = "name")
    private String name;

    /**
     * 单价/晚
     */
    @TableField(value = "price")
    private BigDecimal price;

    /**
     * 可住人数
     */
    @TableField(value = "capacity")
    private Integer capacity;

    /**
     * 床型
     */
    @TableField(value = "bed_type")
    private String bedType;

    /**
     * 面积
     */
    @TableField(value = "area")
    private String area;

    /**
     * 房间设施列表（JSON 字符串格式，如 ["WiFi","空调"]）
     */
    @TableField(value = "amenities")
    private String amenities;

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