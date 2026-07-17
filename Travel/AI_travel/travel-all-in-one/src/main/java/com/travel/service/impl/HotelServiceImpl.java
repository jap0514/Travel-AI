package com.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.entity.Hotel;
import com.travel.entity.HotelRoom;
import com.travel.entity.HotelRoomType;
import com.travel.mapper.HotelRoomMapper;
import com.travel.mapper.HotelRoomTypeMapper;
import com.travel.service.HotelService;
import com.travel.mapper.HotelMapper;
import com.travel.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
* @author 13922
* @description 针对表【hotel(酒店信息)】的数据库操作Service实现
* @createDate 2026-07-14 16:01:42
*/
@Service
public class HotelServiceImpl extends ServiceImpl<HotelMapper, Hotel>
    implements HotelService{

    @Autowired
    private HotelMapper hotelMapper;

    @Autowired
    private HotelRoomTypeMapper hotelRoomTypeMapper;

    @Autowired
    private HotelRoomMapper hotelRoomMapper;

    @Autowired
    private com.travel.mapper.HotelBookingMapper hotelBookingMapper;

    /**
     * 根据城市获取该城市的酒店信息
     * @param city
     * @return
     */
    @Override
    public List<HotelVO> getAllHotelInfo(String city) {
        LambdaQueryWrapper<Hotel> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Hotel::getCity,city);

        List<Hotel> result = hotelMapper.selectList(lambdaQueryWrapper);
        List<HotelVO> voList=result.stream().map(s->{
            HotelVO vo = new HotelVO();
            vo.setHotelId(s.getHotelId());
            vo.setAddress(s.getAddress());
            vo.setCity(s.getCity());
            vo.setDescription(s.getDescription());
            vo.setCreateTime(s.getCreateTime());
            vo.setFacilities(s.getFacilities());
            vo.setLatitude(s.getLatitude());
            vo.setName(s.getName());
            vo.setUpdateTime(s.getUpdateTime());
            vo.setStar(s.getStar());
            vo.setLongitude(s.getLongitude());
            vo.setContactPhone(s.getContactPhone());
            vo.setMainImage(s.getMainImage());
            return vo;
        }).toList();

        return voList;
    }

    /**
     * 根据酒店ID获取酒店的房间类型信息
     * @param hotelId
     * @return
     */
    @Override
    public List<HotelRoomTypeVO> getHotelRoomType(Long hotelId) {
        LambdaQueryWrapper<HotelRoomType> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(HotelRoomType::getHotelId,hotelId);

        List<HotelRoomType> result = hotelRoomTypeMapper.selectList(lambdaQueryWrapper);

        List<HotelRoomTypeVO> voList=result.stream().map(s->{
            HotelRoomTypeVO vo = new HotelRoomTypeVO();
            vo.setRoomTypeId(s.getRoomTypeId());
            vo.setHotelId(s.getHotelId());
            vo.setAmenities(s.getAmenities());
            vo.setHotelName(hotelMapper.selectById(hotelId).getName());
            vo.setArea(s.getArea());
            vo.setBedType(s.getBedType());
            vo.setCapacity(s.getCapacity());
            vo.setPrice(s.getPrice());
            vo.setName(s.getName());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            return vo;
        }).toList();
        return voList;
    }

    /**
     * 根据酒店ID、房间类型ID、房间号查询房间信息
     * @param hotelId 酒店ID
     * @param roomTypeId 房间类型ID
     * @param roomNo 房间号
     * @return
     */
    @Override
    public List<HotelRoomVO> getHotelRoom(Long hotelId, Long roomTypeId, String roomNo) {
        LambdaQueryWrapper<HotelRoom> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(HotelRoom::getHotelId, hotelId);
        lambdaQueryWrapper.eq(HotelRoom::getRoomTypeId, roomTypeId);
        lambdaQueryWrapper.eq(HotelRoom::getRoomNo, roomNo);

        List<HotelRoom> result = hotelRoomMapper.selectList(lambdaQueryWrapper);

        List<HotelRoomVO> voList = result.stream().map(s -> {
            HotelRoomVO vo = new HotelRoomVO();
            vo.setRoomId(s.getRoomId());
            vo.setHotelId(s.getHotelId());
            vo.setRoomTypeId(s.getRoomTypeId());
            vo.setRoomNo(s.getRoomNo());
            vo.setFloor(s.getFloor());
            vo.setStatusName(s.getStatus() == 1 ? "可用" : "不可用");
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            // 查询关联数据
            vo.setHotelName(hotelMapper.selectById(hotelId).getName());
            vo.setRoomTypeName(hotelRoomTypeMapper.selectById(roomTypeId).getName());
            return vo;
        }).toList();
        return voList;
    }

    @Override
    public List<HotelEmptyRoomVO> selectEmptyRoom(String city, String startDate, Long days) {
        // 计算退房日期
        DateTimeFormatter formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime newStartDate = LocalDateTime.parse(startDate, formatter);

        LocalDateTime endDate = newStartDate.plusDays(days);

        //查询空房间
        List<HotelEmptyRoomVO> hotelBookings = hotelBookingMapper.selectEmptyRoom(city, newStartDate, endDate);



        return hotelBookings;
    }
}




