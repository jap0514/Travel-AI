package com.travel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 酒店订单
 * @TableName hotel_booking
 */
@TableName(value ="hotel_booking")
@Data
public class HotelBooking implements Serializable {
    /**
     * 订单ID
     */
    @TableId(value = "booking_id", type = IdType.AUTO)
    private Long bookingId;

    /**
     * 订单号
     */
    @TableField(value = "order_no")
    private String orderNo;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 物理房间ID
     */
    @TableField(value = "room_id")
    private Long roomId;

    /**
     * 入住日期
     */
    @TableField(value = "check_in_date")
    private LocalDateTime checkInDate;

    /**
     * 退房日期
     */
    @TableField(value = "check_out_date")
    private LocalDateTime checkOutDate;

    /**
     * 总价
     */
    @TableField(value = "total_price")
    private BigDecimal totalPrice;

    /**
     * 入住人姓名
     */
    @TableField(value = "guest_name")
    private String guestName;

    /**
     * 电话
     */
    @TableField(value = "guest_phone")
    private String guestPhone;

    /**
     * 特殊要求
     */
    @TableField(value = "special_request")
    private String specialRequest;

    /**
     * 状态: 0待支付, 1已支付, 2已确认, 3已取消, 4已完成
     */
    @TableField(value = "status")
    private Integer status;

    /**
     * 支付时间
     */
    @TableField(value = "payment_time")
    private LocalDateTime paymentTime;

    /**
     * 支付交易流水号
     */
    @TableField(value = "transaction_id")
    private String transactionId;

    /**
     * 取消时间
     */
    @TableField(value = "cancel_time")
    private LocalDateTime cancelTime;

    /**
     * 取消原因
     */
    @TableField(value = "cancel_reason")
    private String cancelReason;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}