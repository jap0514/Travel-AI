package com.travel.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "酒店房间信息视图")
public class HotelRoomVO {

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 所属酒店ID
     */
    private Long hotelId;

    /**
     * 所属酒店
     */
    private String hotelName;

    /**
     * 所属房型ID
     */
    private Long roomTypeId;

    /**
     * 所属房型
     */
    private String roomTypeName;

    /**
     * 房间号(301、502)
     */
    private String roomNo;

    /**
     * 楼层
     */
    private Integer floor;

    /**
     * 1可用 0不可用(装修/维修)
     */
    private String statusName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
