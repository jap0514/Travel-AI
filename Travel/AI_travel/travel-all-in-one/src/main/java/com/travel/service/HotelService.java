package com.travel.service;

import com.travel.dto.HotelAdminDTO;
import com.travel.entity.Hotel;
import com.baomidou.mybatisplus.extension.service.IService;
import com.travel.vo.*;

import java.math.BigDecimal;
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
     * @param city        城市名（必填）
     * @param keyword     搜索关键字（可选，按酒店名称模糊匹配）
     * @param minStar     最低星级 1-5（可选）
     * @param minPrice    最低价格（可选）
     * @param maxPrice    最高价格（可选）
     * @param facilities  必须包含的设施列表（可选，AND 关系：酒店必须同时包含所有设施）
     * @return 酒店列表
     */
    List<HotelVO> getAllHotelInfo(String city, String keyword, Integer minStar, BigDecimal minPrice, BigDecimal maxPrice, List<String> facilities);

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

    /**
     * 新增酒店（写操作会自动清除缓存）
     * @param dto 酒店参数
     * @return 新创建的酒店
     */
    HotelVO createHotel(HotelAdminDTO dto);

    /**
     * 修改酒店（写操作会自动清除缓存）
     * @param dto 酒店参数（必须含 hotelId）
     * @return 更新后的酒店
     */
    HotelVO updateHotel(HotelAdminDTO dto);

    /**
     * 删除酒店（写操作会自动清除缓存）
     * @param hotelId 酒店ID
     * @return 是否删除成功
     */
    boolean deleteHotel(Long hotelId);
}
