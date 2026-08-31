package com.travel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.entity.HotelRoomType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author 13922
 * @description 针对表【hotel_room_type(房型表)】的数据库操作 Mapper
 * @Entity com.travel.entity.HotelRoomType
 */
@Mapper
public interface HotelRoomTypeMapper extends BaseMapper<HotelRoomType> {

    /**
     * 查询某酒店所有房型的最低单价（用于 ES hotel_v1.minPrice 字段）
     * <p>
     * 子查询形式：SELECT MIN(price) FROM hotel_room_type WHERE hotel_id = ?
     *
     * @param hotelId 酒店主键
     * @return 最低价格；若该酒店无房型则返回 null
     */
    @Select("SELECT MIN(price) FROM hotel_room_type WHERE hotel_id = #{hotelId}")
    BigDecimal selectMinPriceByHotelId(@Param("hotelId") Long hotelId);

    /**
     * 查询某酒店房型的最近更新时间
     * <p>
     * 用于增量同步时判断"房型表变了 → hotel 也需要重索引"
     *
     * @param hotelId 酒店主键
     * @return 最近房型 update_time，若无房型则返回 null
     */
    @Select("SELECT MAX(update_time) FROM hotel_room_type WHERE hotel_id = #{hotelId}")
    LocalDateTime selectMaxUpdateTimeByHotelId(@Param("hotelId") Long hotelId);
}
