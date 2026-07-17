package com.travel.controller;

import com.travel.common.Result;
import com.travel.dto.CancelOrderDTO;
import com.travel.dto.HotelBookingDTO;
import com.travel.dto.PayOrderDTO;
import com.travel.dto.QueryBookingDTO;
import com.travel.mapper.HotelBookingMapper;
import com.travel.service.HotelBookingService;
import com.travel.service.HotelService;
import com.travel.vo.HotelBookingVO;
import com.travel.vo.HotelEmptyRoomVO;
import com.travel.vo.HotelRoomTypeVO;
import com.travel.vo.HotelRoomVO;
import com.travel.vo.HotelVO;
import com.travel.vo.PageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/hotel")
public class HotelController {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private HotelBookingService hotelBookingService;

    /**
     * 根据城市获取酒店信息
     * @param city
     * @return
     */
    @GetMapping("/hotelInfo/getHotelByCity")
    public Result<List<HotelVO>> getHotelByCity(@RequestParam("city") String city){
        List<HotelVO> hotelVOList = new ArrayList<>();
        hotelVOList=hotelService.getAllHotelInfo(city);
        return Result.success(hotelVOList);
    }

    /**
     * 根据酒店ID查询房间类型信息
     * @param hotelId
     * @return
     */
    @GetMapping("/hotelInfo/getHotelRoomType")
    public Result<List<HotelRoomTypeVO>> getHotelRoomType(@RequestParam("hotelId") Long hotelId){
        List<HotelRoomTypeVO> hotelRoomTypeVOList=new ArrayList<>();
        hotelRoomTypeVOList=hotelService.getHotelRoomType(hotelId);
        return Result.success(hotelRoomTypeVOList);
    }

    /**
     * 根据酒店ID、房间类型ID、房间号查询房间信息
     * @param hotelId 酒店ID
     * @param roomTypeId 房间类型ID
     * @param roomNo 房间号
     * @return
     */
    @GetMapping("/hotelInfo/getHotelRoom")
    public Result<List<HotelRoomVO>> getHotelRoom(
            @RequestParam("hotelId") Long hotelId,
            @RequestParam("roomTypeId") Long roomTypeId,
            @RequestParam("roomNo") String roomNo){
        List<HotelRoomVO> hotelRoomVOList = hotelService.getHotelRoom(hotelId, roomTypeId, roomNo);
        return Result.success(hotelRoomVOList);
    }


    /**
     * 查询是否存在空房间
     * @param city
     * @param startDate
     * @param days
     * @return
     */
    @GetMapping("/hotelInfo/selectEmptyRoom")
    public Result<List<HotelEmptyRoomVO>> selectEmptyRoom(@RequestParam("city") String city,
                                                          @RequestParam("startDate") String startDate,
                                                          @RequestParam("days") Long days){
        List<HotelEmptyRoomVO> emptyRoomVOList=hotelService.selectEmptyRoom(city,startDate,days);
        return Result.success(emptyRoomVOList);
    }


    @PostMapping("/order/createOrder")
    public Result<HotelBookingVO> createOrder(@RequestBody HotelBookingDTO dto,
                                              @RequestAttribute Long userId){
        HotelBookingVO hotelBookingVO=hotelBookingService.createOrder(dto,userId);
        return Result.success(hotelBookingVO);
    }

    /**
     * 分页查询用户订单列表
     * @param dto 查询条件
     * @return 分页订单列表
     */
    @GetMapping("/order/list")
    public Result<PageVO<HotelBookingVO>> getUserOrders(QueryBookingDTO dto){
        PageVO<HotelBookingVO> result = hotelBookingService.getUserOrders(dto);
        return Result.success(result);
    }

    /**
     * 根据订单号查询订单详情
     * @param orderNo 订单号
     * @return 订单详情
     */
    @GetMapping("/order/{orderNo}")
    public Result<HotelBookingVO> getOrderByOrderNo(@PathVariable("orderNo") String orderNo){
        HotelBookingVO result = hotelBookingService.getOrderByOrderNo(orderNo);
        return Result.success(result);
    }

    /**
     * 取消订单
     * @param orderNo 订单号
     * @param dto 取消信息
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/order/{orderNo}/cancel")
    public Result<HotelBookingVO> cancelOrder(@PathVariable("orderNo") String orderNo,
                                               @RequestBody CancelOrderDTO dto,
                                               @RequestAttribute Long userId){
        HotelBookingVO result = hotelBookingService.cancelOrder(orderNo, userId, dto);
        return Result.success(result);
    }

    /**
     * 完成订单（退房）
     * @param orderNo 订单号
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/order/{orderNo}/complete")
    public Result<HotelBookingVO> completeOrder(@PathVariable("orderNo") String orderNo,
                                                  @RequestAttribute Long userId){
        HotelBookingVO result = hotelBookingService.completeOrder(orderNo, userId);
        return Result.success(result);
    }

    /**
     * 支付订单
     * @param orderNo 订单号
     * @param dto 支付信息
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/order/{orderNo}/pay")
    public Result<HotelBookingVO> payOrder(@PathVariable("orderNo") String orderNo,
                                          @RequestBody PayOrderDTO dto,
                                          @RequestAttribute Long userId){
        HotelBookingVO result = hotelBookingService.payOrder(orderNo, userId, dto);
        return Result.success(result);
    }
}
