package com.travel.controller;

import com.travel.annotation.DistributedLock;
import com.travel.annotation.Idempotent;
import com.travel.annotation.RateLimiter;
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
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/hotel")
@Validated
public class HotelController {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private HotelBookingService hotelBookingService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 订单幂等 Token 有效期：5分钟 */
    private static final long IDEMPOTENT_TOKEN_EXPIRE = 300;

    /**
     * 根据城市获取酒店信息
     * @param city
     * @return
     */
    @GetMapping("/hotelInfo/getHotelByCity")
    @RateLimiter(resourceName = "HotelController:getHotelByCity")
    @Timed(value = "hotel.getHotelByCity", description = "按城市查询酒店耗时", percentiles = {0.5, 0.90, 0.95, 0.99})
    public Result<List<HotelVO>> getHotelByCity(
            @RequestParam("city")
            @NotBlank(message = "城市不能为空")
            @Pattern(regexp = "^[\\u4e00-\\u9fa5]{2,10}$", message = "城市名格式不正确（2-10个中文）")
            String city){
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

    /**
     * 验证指定房间在日期范围内是否仍然可预约
     * @param hotelId 酒店ID
     * @param roomTypeId 房型ID
     * @param roomNo 房间号
     * @param checkInDate 入住日期
     * @param checkOutDate 退房日期
     * @return true=可预约，false=已被预订
     */
    @GetMapping("/api/hotel/verifyRoom")
    public Result<Map<String, Object>> verifyRoom(@RequestParam("hotelId") Long hotelId,
                                                  @RequestParam("roomTypeId") Long roomTypeId,
                                                  @RequestParam("roomNo") String roomNo,
                                                  @RequestParam("checkInDate") String checkInDate,
                                                  @RequestParam("checkOutDate") String checkOutDate) {
        boolean available = hotelBookingService.verifyRoomAvailable(hotelId, roomTypeId, roomNo, checkInDate, checkOutDate);
        Map<String, Object> data = new HashMap<>();
        data.put("available", available);
        return Result.success(data);
    }

    /**
     * 获取订单幂等 Token
     * 前端下单前先调用此接口获取 Token，下单时携带 Token 防止重复提交
     *
     * @return 幂等 Token
     */
    @GetMapping("/order/token")
    public Result<Map<String, String>> getIdempotentToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = "idempotent:order:token:" + token;
        redisTemplate.opsForValue().set(redisKey, "1", IDEMPOTENT_TOKEN_EXPIRE, TimeUnit.SECONDS);

        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        result.put("expireSeconds", String.valueOf(IDEMPOTENT_TOKEN_EXPIRE));

        return Result.success(result);
    }

    /**
     * 创建酒店订单（带幂等性保护）
     * 前端需先调用 GET /order/token 获取 Token，然后携带 Token 请求此接口
     *
     * @param dto 订单信息（含幂等 Token）
     * @return 订单信息
     */
    @PostMapping("/order/createOrder")
    @RateLimiter(resourceName = "HotelController:createOrder", count = 50, timeout = 1000)
    @Idempotent(key = "#dto.idempotentToken", expireTime = 300, message = "请勿重复提交订单")
    @Timed(value = "hotel.createOrder", description = "创建订单耗时", percentiles = {0.5, 0.90, 0.95, 0.99})
    public Result<HotelBookingVO> createOrder(@RequestBody @Valid HotelBookingDTO dto){
        HotelBookingVO hotelBookingVO=hotelBookingService.createOrder(dto,dto.getUserId());
        return Result.success(hotelBookingVO);
    }

    /**
     * 分页查询用户订单列表
     * @param dto 查询条件
     * @return 分页订单列表
     */
    @GetMapping("/order/list")
    public Result<PageVO<HotelBookingVO>> getUserOrders(@Valid QueryBookingDTO dto){
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
     * 支付订单（带分布式锁，防止并发支付）
     * @param orderNo 订单号
     * @param dto 支付信息
     * @param userId 用户ID
     * @return 操作结果
     */
    @PutMapping("/order/{orderNo}/pay")
    @DistributedLock(key = "'lock:order:pay:' + #orderNo", waitTime = 5, leaseTime = 10)
    @Timed(value = "hotel.payOrder", description = "支付订单耗时", percentiles = {0.5, 0.90, 0.95, 0.99})
    public Result<HotelBookingVO> payOrder(@PathVariable("orderNo") String orderNo,
                                          @RequestBody PayOrderDTO dto,
                                          @RequestAttribute Long userId){
        HotelBookingVO result = hotelBookingService.payOrder(orderNo, userId, dto);
        return Result.success(result);
    }
}
