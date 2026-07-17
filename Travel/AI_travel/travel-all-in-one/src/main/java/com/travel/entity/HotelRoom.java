package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 物理房间表
 * @TableName hotel_room
 */
@TableName(value ="hotel_room")
@Data
public class HotelRoom implements Serializable {
    /**
     * 
     */
    @TableId(value = "room_id", type = IdType.AUTO)
    private Long roomId;

    /**
     * 所属酒店
     */
    @TableField(value = "hotel_id")
    private Long hotelId;

    /**
     * 所属房型
     */
    @TableField(value = "room_type_id")
    private Long roomTypeId;

    /**
     * 房间号(301、502)
     */
    @TableField(value = "room_no")
    private String roomNo;

    /**
     * 楼层
     */
    @TableField(value = "floor")
    private Integer floor;

    /**
     * 1可用 0不可用(装修/维修)
     */
    @TableField(value = "status")
    private Integer status;

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