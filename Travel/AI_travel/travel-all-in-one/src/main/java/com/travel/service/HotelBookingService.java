package com.travel.service;

import com.travel.dto.CancelOrderDTO;
import com.travel.dto.HotelBookingDTO;
import com.travel.dto.PayOrderDTO;
import com.travel.dto.QueryBookingDTO;
import com.travel.entity.HotelBooking;
import com.baomidou.mybatisplus.extension.service.IService;
import com.travel.vo.HotelBookingVO;
import com.travel.vo.PageVO;

/**
* @author 13922
* @description 针对表【hotel_booking(酒店订单)】的数据库操作Service
* @createDate 2026-07-14 16:41:42
*/
public interface HotelBookingService extends IService<HotelBooking> {

    /**
     * 创建订单，预订酒店房间
     * @param dto 预订信息
     * @param userId 用户ID
     * @return 订单信息
     */
    HotelBookingVO createOrder(HotelBookingDTO dto, Long userId);

    /**
     * 分页查询用户订单列表
     * @param dto 查询条件
     * @return 分页订单列表
     */
    PageVO<HotelBookingVO> getUserOrders(QueryBookingDTO dto);

    /**
     * 根据订单号查询订单详情
     * @param orderNo 订单号
     * @return 订单详情
     */
    HotelBookingVO getOrderByOrderNo(String orderNo);

    /**
     * 取消订单
     * @param orderNo 订单号
     * @param userId 用户ID（校验权限）
     * @param dto 取消信息
     * @return 操作结果
     */
    HotelBookingVO cancelOrder(String orderNo, Long userId, CancelOrderDTO dto);

    /**
     * 完成订单（退房）
     * @param orderNo 订单号
     * @param userId 用户ID（校验权限）
     * @return 操作结果
     */
    HotelBookingVO completeOrder(String orderNo, Long userId);

    /**
     * 支付订单
     * @param orderNo 订单号
     * @param userId 用户ID（校验权限）
     * @param dto 支付信息
     * @return 操作结果
     */
    HotelBookingVO payOrder(String orderNo, Long userId, PayOrderDTO dto);
}
