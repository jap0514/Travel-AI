package com.travel.service;

import com.travel.entity.Hotel;
import com.baomidou.mybatisplus.extension.service.IService;
import com.travel.vo.*;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author 13922
* @description 针对表【hotel(酒店信息)】的数据库操作Service
* @createDate 2026-07-14 16:01:42
*/
public interface HotelService extends IService<Hotel> {

    /**
     * 根据城市获取该城市的酒店信息
     * @param city
     * @return
     */
    List<HotelVO> getAllHotelInfo(String city);

    /**
     * 根据酒店ID获取酒店详细信息
     * @param hotelId 酒店ID
     * @return 酒店详细信息，不存在时返回 null
     */
    HotelVO getHotelById(Long hotelId);

    /**
     * 根据酒店ID获取酒店的房间类型信息
     * @param hotelId
     * @return
     */
    List<HotelRoomTypeVO> getHotelRoomType(Long hotelId);

    /**
     * 根据酒店ID、房间类型ID、房间号查询房间信息
     * @param hotelId 酒店ID
     * @param roomTypeId 房间类型ID
     * @param roomNo 房间号
     * @return
     */
    List<HotelRoomVO> getHotelRoom(Long hotelId, Long roomTypeId, String roomNo);

    /**
     * 根据城市、日期、天数来查询当地的酒店是否有空房间
     * @param city 旅游的城市
     * @param startDate 开始的日期
     * @param days 持续的天数
     * @return
     */
    List<HotelEmptyRoomVO> selectEmptyRoom(String city, String startDate, Long days);
}
