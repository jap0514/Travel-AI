package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.common.ResultCode;
import com.travel.exception.BusinessException;
import com.travel.dto.CancelOrderDTO;
import com.travel.dto.HotelBookingDTO;
import com.travel.dto.PayOrderDTO;
import com.travel.dto.QueryBookingDTO;
import com.travel.entity.Hotel;
import com.travel.entity.HotelBooking;
import com.travel.entity.HotelRoom;
import com.travel.entity.HotelRoomType;
import com.travel.mapper.HotelBookingMapper;
import com.travel.mapper.HotelMapper;
import com.travel.mapper.HotelRoomMapper;
import com.travel.mapper.HotelRoomTypeMapper;
import com.travel.mq.OrderTimeoutProducer;
import com.travel.service.HotelBookingService;
import com.travel.util.TraceIdUtil;
import com.travel.vo.HotelBookingVO;
import com.travel.vo.HotelEmptyRoomVO;
import com.travel.vo.PageVO;
import com.travel.vo.HotelRoomVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author 13922
* @description 针对表【hotel_booking(酒店订单)】的数据库操作Service实现
* @createDate 2026-07-14 16:41:42
*/
@Slf4j
@Service
public class HotelBookingServiceImpl extends ServiceImpl<HotelBookingMapper, HotelBooking>
    implements HotelBookingService{

    @Autowired
    private HotelBookingMapper hotelBookingMapper;

    @Autowired
    private HotelMapper hotelMapper;

    @Autowired
    private HotelRoomMapper hotelRoomMapper;

    @Autowired
    private HotelRoomTypeMapper hotelRoomTypeMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private OrderTimeoutProducer orderTimeoutProducer;

    @Autowired
    private TraceIdUtil traceIdUtil;

    @Override
    public HotelBookingVO createOrder(HotelBookingDTO dto, Long userId) {
        Long hotelId = dto.getHotelId();
        String roomNo = dto.getRoomNo();
        LambdaQueryWrapper<HotelRoom> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(HotelRoom::getHotelId,hotelId);
        lambdaQueryWrapper.eq(HotelRoom::getRoomNo,roomNo);
        HotelRoom hotelRoom = hotelRoomMapper.selectOne(lambdaQueryWrapper);
        // ========== 步骤1：尝试获取 Redis 分布式锁 ==========
        // 锁的粒度：每个房间一把锁
        // 如果获取锁失败 → 抛出异常："该房间正在被预订，请稍后重试"
        String key="lock:hotel:"+hotelId+":room:"+roomNo;
        RLock lock = redissonClient.getLock(key);
        try {
            boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if(!locked){
                throw new BusinessException(ResultCode.ROOM_NOT_AVAILABLE, "该房间正在被预订，请稍后重试！");
            }

            //校验房间是否为空
            Long count = hotelBookingMapper.checkEmptyRoom(hotelId, roomNo, dto.getCheckInDate());
            if(count != null && count > 0){
                //查询到需要预订的房间存在订单时
                throw new BusinessException(ResultCode.ROOM_NOT_AVAILABLE, "该房间在入住当天已被预订");
            }

            //计算价格
            Long roomTypeId = dto.getRoomTypeId();
            BigDecimal singlePrice = hotelRoomTypeMapper.selectById(roomTypeId).getPrice();
            long daysBetween = ChronoUnit.DAYS.between(dto.getCheckInDate(), dto.getCheckOutDate());
            BigDecimal totalPrice=singlePrice.multiply(BigDecimal.valueOf(daysBetween));

            //生成订单号
            String dateStr=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Long seq=redisTemplate.opsForValue().increment("order:hotel:seq");
            String orderNo="HTB"+dateStr+String.format("%06d",seq);

            //构造订单对象
            HotelBooking booking = new HotelBooking();
            booking.setOrderNo(orderNo);
            booking.setUserId(userId);
            booking.setRoomId(hotelRoom.getRoomId());
            booking.setCheckInDate(dto.getCheckInDate());
            booking.setCheckOutDate(dto.getCheckOutDate());
            booking.setTotalPrice(totalPrice);
            booking.setGuestName(dto.getGuestName());
            booking.setGuestPhone(dto.getGuestPhone());
            booking.setSpecialRequest(dto.getSpecialRequest());
            booking.setStatus(0); // 待支付
            booking.setCreateTime(LocalDateTime.now());
            booking.setUpdateTime(LocalDateTime.now());

            //插入数据库
            int insert = hotelBookingMapper.insert(booking);
            if(insert==0){
                log.error("保存订单消息失败");
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "订单保存失败");
            }

            //发送 RocketMQ 延迟消息，30分钟后自动处理超时订单
            orderTimeoutProducer.sendTimeoutMessage(orderNo, userId, traceIdUtil.getTraceId());

            //构建VO返回
            HotelBookingVO vo = new HotelBookingVO();
            vo.setSuccess(true);
            vo.setBookingId(booking.getBookingId());
            vo.setOrderNo(orderNo);
            vo.setHotelId(hotelId);
            vo.setHotelName(hotelMapper.selectById(hotelId).getName());
            vo.setRoomTypeId(roomTypeId);
            vo.setRoomTypeName(hotelRoomTypeMapper.selectById(roomTypeId).getName());
            vo.setRoomNo(roomNo);
            vo.setCheckInDate(dto.getCheckInDate());
            vo.setCheckOutDate(dto.getCheckOutDate());
            vo.setDays(daysBetween);
            vo.setTotalPrice(totalPrice);
            vo.setGuestName(dto.getGuestName());
            vo.setGuestPhone(dto.getGuestPhone());
            vo.setStatus(0);
            vo.setStatusName("待支付");
            vo.setCreateTime(booking.getCreateTime());

            return vo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "获取锁被中断");
        } finally {
            if(lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }

    }



    @Override
    public PageVO<HotelBookingVO> getUserOrders(QueryBookingDTO dto) {
        // 构建分页对象
        IPage<HotelBooking> page = new Page<>(dto.getPage(), dto.getSize());

        // 构建查询条件
        LambdaQueryWrapper<HotelBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotelBooking::getUserId, dto.getUserId());
        if (dto.getStatus() != null) {
            wrapper.eq(HotelBooking::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(HotelBooking::getCreateTime); // 按创建时间倒序

        // 分页查询
        IPage<HotelBooking> result = hotelBookingMapper.selectPage(page, wrapper);

        // 转换为 VO
        List<HotelBookingVO> voList = result.getRecords().stream().map(booking -> {
            HotelBookingVO vo = new HotelBookingVO();
            vo.setSuccess(true);
            vo.setBookingId(booking.getBookingId());
            vo.setOrderNo(booking.getOrderNo());
            vo.setCheckInDate(booking.getCheckInDate());
            vo.setCheckOutDate(booking.getCheckOutDate());
            vo.setTotalPrice(booking.getTotalPrice());
            vo.setGuestName(booking.getGuestName());
            vo.setGuestPhone(booking.getGuestPhone());
            vo.setStatus(booking.getStatus());
            vo.setStatusName(getStatusName(booking.getStatus()));
            vo.setCreateTime(booking.getCreateTime());

            // 查询关联的酒店和房型信息
            HotelRoom room = hotelRoomMapper.selectById(booking.getRoomId());
            if (room != null) {
                vo.setRoomNo(room.getRoomNo());
                Hotel hotel = hotelMapper.selectById(room.getHotelId());
                if (hotel != null) {
                    vo.setHotelId(hotel.getHotelId());
                    vo.setHotelName(hotel.getName());
                }
                HotelRoomType roomType = hotelRoomTypeMapper.selectById(room.getRoomTypeId());
                if (roomType != null) {
                    vo.setRoomTypeId(roomType.getRoomTypeId());
                    vo.setRoomTypeName(roomType.getName());
                }
            }

            // 计算入住天数
            if (booking.getCheckInDate() != null && booking.getCheckOutDate() != null) {
                long days = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
                vo.setDays(days);
            }

            return vo;
        }).toList();

        return PageVO.of(voList, result.getTotal(), result.getCurrent(), result.getSize());
    }

    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已确认";
            case 3 -> "已取消";
            case 4 -> "已完成";
            default -> "未知";
        };
    }

    @Override
    public HotelBookingVO getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<HotelBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotelBooking::getOrderNo, orderNo);
        HotelBooking booking = hotelBookingMapper.selectOne(wrapper);
        if (booking == null) {
            return null;
        }

        HotelBookingVO vo = new HotelBookingVO();
        vo.setSuccess(true);
        vo.setBookingId(booking.getBookingId());
        vo.setOrderNo(booking.getOrderNo());
        vo.setCheckInDate(booking.getCheckInDate());
        vo.setCheckOutDate(booking.getCheckOutDate());
        vo.setTotalPrice(booking.getTotalPrice());
        vo.setGuestName(booking.getGuestName());
        vo.setGuestPhone(booking.getGuestPhone());
        vo.setStatus(booking.getStatus());
        vo.setStatusName(getStatusName(booking.getStatus()));
        vo.setCreateTime(booking.getCreateTime());

        // 查询关联的酒店和房型信息
        HotelRoom room = hotelRoomMapper.selectById(booking.getRoomId());
        if (room != null) {
            vo.setRoomNo(room.getRoomNo());
            Hotel hotel = hotelMapper.selectById(room.getHotelId());
            if (hotel != null) {
                vo.setHotelId(hotel.getHotelId());
                vo.setHotelName(hotel.getName());
            }
            HotelRoomType roomType = hotelRoomTypeMapper.selectById(room.getRoomTypeId());
            if (roomType != null) {
                vo.setRoomTypeId(roomType.getRoomTypeId());
                vo.setRoomTypeName(roomType.getName());
            }
        }

        // 计算入住天数
        if (booking.getCheckInDate() != null && booking.getCheckOutDate() != null) {
            long days = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
            vo.setDays(days);
        }

        return vo;
    }

    @Override
    public HotelBookingVO cancelOrder(String orderNo, Long userId, CancelOrderDTO dto) {
        // 查询订单
        LambdaQueryWrapper<HotelBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotelBooking::getOrderNo, orderNo);
        HotelBooking booking = hotelBookingMapper.selectOne(wrapper);

        // 校验订单是否存在
        if (booking == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }

        // 校验是否是本人的订单
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权取消此订单");
        }

        // 校验订单状态：只有 0待支付、1已支付 可以取消（2已确认只能走退房流程）
        Integer status = booking.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "此订单无法取消，当前状态：" + getStatusName(status));
        }

        //TODO: 如果是已支付状态下取消的订单，就需要做退款处理

        // 执行取消：更新状态为已取消，记录取消时间和原因
        booking.setStatus(3); // 已取消
        booking.setCancelTime(LocalDateTime.now());
        booking.setCancelReason(dto.getCancelReason());
        booking.setUpdateTime(LocalDateTime.now());
        hotelBookingMapper.updateById(booking);

        // 返回更新后的订单信息
        return getOrderByOrderNo(orderNo);
    }

    @Override
    public HotelBookingVO completeOrder(String orderNo, Long userId) {
        // 查询订单
        LambdaQueryWrapper<HotelBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotelBooking::getOrderNo, orderNo);
        HotelBooking booking = hotelBookingMapper.selectOne(wrapper);

        // 校验订单是否存在
        if (booking == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }

        // 校验是否是本人的订单
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }

        // 校验订单状态：只有 2已确认 可以完成
        if (booking.getStatus() != 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "此订单无法完成入住，当前状态：" + getStatusName(booking.getStatus()));
        }

        // 执行完成：更新状态为已完成
        booking.setStatus(4); // 已完成
        booking.setUpdateTime(LocalDateTime.now());
        hotelBookingMapper.updateById(booking);

        // 返回更新后的订单信息
        return getOrderByOrderNo(orderNo);
    }

    @Override
    public HotelBookingVO payOrder(String orderNo, Long userId, PayOrderDTO dto) {
        // 查询订单
        LambdaQueryWrapper<HotelBooking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotelBooking::getOrderNo, orderNo);
        HotelBooking booking = hotelBookingMapper.selectOne(wrapper);

        // 校验订单是否存在
        if (booking == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在");
        }

        // 校验是否是本人的订单
        if (!booking.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }

        // 校验订单状态：只有 0待支付 可以支付
        if (booking.getStatus() != 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "此订单无法支付，当前状态：" + getStatusName(booking.getStatus()));
        }

        // 生成交易流水号（简化版）
        String transactionId = dto.getTransactionId();
        if (transactionId == null || transactionId.isBlank()) {
            transactionId = "PAY_" + orderNo + "_" + System.currentTimeMillis();
        }

        // 执行支付：更新状态为已支付，记录支付时间和交易流水号
        booking.setStatus(1); // 已支付
        booking.setPaymentTime(LocalDateTime.now());
        booking.setTransactionId(transactionId);
        booking.setUpdateTime(LocalDateTime.now());
        hotelBookingMapper.updateById(booking);

        // 返回更新后的订单信息
        return getOrderByOrderNo(orderNo);
    }

    @Override
    public boolean verifyRoomAvailable(Long hotelId, Long roomTypeId, String roomNo,
                                     String checkInDate, String checkOutDate) {
        if (hotelId == null || roomTypeId == null || roomNo == null
                || checkInDate == null || checkOutDate == null) {
            return false;
        }
        LocalDateTime checkIn = LocalDateTime.parse(checkInDate + "T00:00:00");
        LocalDateTime checkOut = LocalDateTime.parse(checkOutDate + "T00:00:00");
        Long roomId = hotelBookingMapper.verifyRoomAvailable(hotelId, roomTypeId, roomNo, checkIn, checkOut);
        return roomId != null && roomId > 0;
    }
}




