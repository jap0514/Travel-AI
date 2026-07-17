package com.travel.mapper;

import com.travel.entity.HotelBooking;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.vo.HotelBookingVO;
import com.travel.vo.HotelEmptyRoomVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author 13922
* @description 针对表【hotel_booking(酒店订单)】的数据库操作Mapper
* @createDate 2026-07-14 16:41:42
* @Entity com.travel.entity.HotelBooking
*/
public interface HotelBookingMapper extends BaseMapper<HotelBooking> {

    /**
     * 根据城市、日期范围查询空房间
     * @param city 城市
     * @param startDate 入住日期
     * @param endDate 退房日期
     * @return
     */
    List<HotelEmptyRoomVO> selectEmptyRoom(@Param("city") String city,
                                           @Param("startDate")LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);


    Long checkEmptyRoom(@Param("hotelId") Long hotelId,
                        @Param("roomNo") String roomNo,
                        @Param("checkInDate") LocalDateTime checkInDate);
}




